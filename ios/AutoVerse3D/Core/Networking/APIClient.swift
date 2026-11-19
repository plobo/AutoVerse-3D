import Foundation

enum APIError: Error, Equatable {
    case http(status: Int, retryAfter: Int?)
    case transport
    case decoding
}

protocol AssistantService: Sendable {
    func ask(_ request: AssistantRequest) async throws -> AssistantResponse
}

protocol ManifestService: Sendable {
    func manifest(id: String) async throws -> AssetManifest
}

/// URLSession client for the AutoVerse backend. The app holds no API keys: the backend owns AI credentials.
struct APIClient: AssistantService, ManifestService {
    let baseURL: URL
    let session: URLSession

    init(baseURL: URL, session: URLSession = .shared) { self.baseURL = baseURL; self.session = session }

    static func fromBundle(_ bundle: Bundle = .main) -> APIClient {
        let raw = bundle.object(forInfoDictionaryKey: "API_BASE_URL") as? String ?? "http://localhost:8787/"
        return APIClient(baseURL: URL(string: raw) ?? URL(string: "http://localhost:8787/")!)
    }

    func makeAskRequest(_ body: AssistantRequest) throws -> URLRequest {
        var r = URLRequest(url: baseURL.appendingPathComponent("v1/ai/automotive-assistant"))
        r.httpMethod = "POST"
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.timeoutInterval = 60
        r.httpBody = try JSONEncoder().encode(body)
        return r
    }

    func ask(_ request: AssistantRequest) async throws -> AssistantResponse {
        try await send(makeAskRequest(request))
    }

    func manifest(id: String) async throws -> AssetManifest {
        var r = URLRequest(url: baseURL.appendingPathComponent("v1/assets/\(id)"))
        r.timeoutInterval = 15
        return try await send(r)
    }

    private func send<T: Decodable>(_ request: URLRequest) async throws -> T {
        let data: Data, response: URLResponse
        do { (data, response) = try await session.data(for: request) } catch { throw APIError.transport }
        guard let http = response as? HTTPURLResponse else { throw APIError.transport }
        guard (200..<300).contains(http.statusCode) else {
            throw APIError.http(status: http.statusCode, retryAfter: (http.value(forHTTPHeaderField: "Retry-After")).flatMap(Int.init))
        }
        do { return try JSONDecoder().decode(T.self, from: data) } catch { throw APIError.decoding }
    }
}

enum AssistantResult: Sendable {
    case online(AssistantResponse)
    case offline(reason: String, matches: [SearchHit])
    case rateLimited(retryAfter: Int?, matches: [SearchHit])
    case invalid(String)
}

/// Online assistant with an offline fallback over bundled learning content.
struct AssistantRepository: Sendable {
    let service: AssistantService
    let index: [SearchDocument]

    func ask(_ question: String, vehicleId: String?, componentId: String?) async -> AssistantResult {
        let q = question.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return .invalid("Type a question first.") }
        if q.count > 500 { return .invalid("Please keep questions under 500 characters.") }
        let offline = { ContentSearch.search(index, query: q, limit: 4) }
        do {
            return .online(try await service.ask(AssistantRequest(question: q, vehicleId: vehicleId, componentId: componentId)))
        } catch APIError.http(429, let retry) {
            return .rateLimited(retryAfter: retry, matches: offline())
        } catch APIError.http(400, _) {
            return .invalid("The assistant couldn't read that question. Try rephrasing it.")
        } catch APIError.transport {
            return .offline(reason: "You're offline, so the AI assistant isn't available.", matches: offline())
        } catch {
            return .offline(reason: "The AI assistant is temporarily unavailable.", matches: offline())
        }
    }
}

"""Scene graph + material definitions shared by the GLB and USDZ exporters."""
from __future__ import annotations

from dataclasses import dataclass, field

from mesh import Mesh, Vec3


def srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def hex_to_linear(hex_color: str) -> tuple[float, float, float]:
    h = hex_color.lstrip("#")
    return tuple(round(srgb_to_linear(int(h[i:i + 2], 16) / 255.0), 5) for i in (0, 2, 4))  # type: ignore


@dataclass
class Material:
    name: str
    base_hex: str
    metallic: float = 0.0
    roughness: float = 0.5
    emissive_hex: str | None = None
    opacity: float = 1.0
    double_sided: bool = False

    @property
    def base_linear(self) -> tuple[float, float, float]:
        return hex_to_linear(self.base_hex)

    @property
    def emissive_linear(self) -> tuple[float, float, float]:
        return hex_to_linear(self.emissive_hex) if self.emissive_hex else (0.0, 0.0, 0.0)

    @property
    def blended(self) -> bool:
        return self.opacity < 1.0


@dataclass
class Node:
    name: str
    pivot: Vec3 = (0.0, 0.0, 0.0)  # world-space origin of this node
    children: list["Node"] = field(default_factory=list)
    mesh: Mesh | None = None  # world-space geometry; exporters convert to local space
    material: str | None = None
    parent: "Node | None" = field(default=None, repr=False)

    def add(self, child: "Node") -> "Node":
        child.parent = self
        self.children.append(child)
        return child

    def group(self, name: str, pivot: Vec3 | None = None) -> "Node":
        return self.add(Node(name, pivot if pivot is not None else self.pivot))

    def part(self, suffix: str, mesh: Mesh, material: str) -> "Node":
        """Adds a mesh child named <self.name>_<suffix> that shares this node's pivot."""
        return self.add(Node(f"{self.name}_{suffix}", self.pivot, mesh=mesh, material=material))

    @property
    def local_translation(self) -> Vec3:
        if self.parent is None:
            return self.pivot
        p = self.parent.pivot
        return (self.pivot[0] - p[0], self.pivot[1] - p[1], self.pivot[2] - p[2])

    def local_mesh(self) -> Mesh | None:
        if self.mesh is None:
            return None
        px, py, pz = self.pivot
        return self.mesh.translated((-px, -py, -pz))

    def walk(self):
        yield self
        for c in self.children:
            yield from c.walk()

    def find(self, name: str) -> "Node | None":
        return next((n for n in self.walk() if n.name == name), None)

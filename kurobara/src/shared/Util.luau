-- KUROBARA · small shared helpers

local TweenService = game:GetService("TweenService")

local Util = {}

function Util.tween(inst: Instance, time: number, props: { [string]: any }, style: Enum.EasingStyle?, dir: Enum.EasingDirection?)
	local tw = TweenService:Create(inst, TweenInfo.new(time, style or Enum.EasingStyle.Quad, dir or Enum.EasingDirection.Out), props)
	tw:Play()
	return tw
end

function Util.weld(a: BasePart, b: BasePart): WeldConstraint
	local w = Instance.new("WeldConstraint")
	w.Part0 = a
	w.Part1 = b
	w.Parent = b
	return w
end

-- Cosmetic part welded onto a character / rig (massless, no collisions, no raycasts)
function Util.deco(size: Vector3, cf: CFrame, color: Color3, parent: Instance, name: string?, material: Enum.Material?): Part
	local p = Instance.new("Part")
	p.Name = name or "Deco"
	p.Size = size
	p.CFrame = cf
	p.Color = color
	p.Material = material or Enum.Material.SmoothPlastic
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.Massless = true
	p.CastShadow = false
	p.Anchored = false
	p.Parent = parent
	return p
end

function Util.mesh(part: BasePart, meshType: Enum.MeshType, scale: Vector3?): SpecialMesh
	local m = Instance.new("SpecialMesh")
	m.MeshType = meshType
	if scale then
		m.Scale = scale
	end
	m.Parent = part
	return m
end

function Util.sound(id: string, parent: Instance, volume: number?, pitch: number?): Sound
	local s = Instance.new("Sound")
	s.SoundId = id
	s.Volume = volume or 0.6
	s.PlaybackSpeed = pitch or 1
	s.RollOffMaxDistance = 90
	s.Parent = parent
	return s
end

function Util.playSound(id: string?, parent: Instance?, volume: number?, pitch: number?)
	if not id or id == "" or not parent then
		return
	end
	local s = Util.sound(id, parent, volume, pitch)
	s:Play()
	s.Ended:Once(function()
		s:Destroy()
	end)
	task.delay(8, function()
		if s.Parent then
			s:Destroy()
		end
	end)
end

function Util.flat(v: Vector3): Vector3
	return Vector3.new(v.X, 0, v.Z)
end

function Util.pick<T>(list: { T }): T
	return list[math.random(1, #list)]
end

function Util.fmtClock(minutes: number): string
	local m = math.floor(minutes)
	return string.format("%02d:%02d", math.floor(m / 60), m % 60)
end

function Util.clamp(x: number, a: number, b: number): number
	return math.max(a, math.min(b, x))
end

return Util

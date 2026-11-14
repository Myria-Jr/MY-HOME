-- KUROBARA · Server blood: persistent evidence (pools, trails, stained clothes, bloody weapons)
-- The flashy, short-lived stuff (droplets, spray, splats, screen blood) is simulated on each
-- client from Remotes.BloodFX so it stays smooth.

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Util = require(Shared.Util)
local Remotes = ReplicatedStorage:WaitForChild("Remotes")

local Blood = {}
local S -- services, set in Init

local poolFolder: Folder

function Blood.Init(services)
	S = services
	poolFolder = workspace.Evidence:FindFirstChild("Blood") :: Folder
	if not poolFolder then
		poolFolder = Instance.new("Folder")
		poolFolder.Name = "Blood"
		poolFolder.Parent = workspace.Evidence
	end
end

-- ── Client FX broadcast ──────────────────────────────────────────────────────
function Blood.FX(kind: string, data: { [string]: any })
	Remotes.BloodFX:FireAllClients(kind, data)
end

-- ── Pools (evidence) ─────────────────────────────────────────────────────────
local groundParams = RaycastParams.new()
groundParams.FilterType = Enum.RaycastFilterType.Exclude
groundParams.CollisionGroup = "BloodRay"

local function groundAt(pos: Vector3): (Vector3?, Vector3?)
	groundParams.FilterDescendantsInstances = { poolFolder }
	local hit = workspace:Raycast(pos + Vector3.new(0, 2, 0), Vector3.new(0, -12, 0), groundParams)
	if hit then
		return hit.Position, hit.Normal
	end
	return nil, nil
end

-- Creates (or grows) a pool. size = final diameter in studs.
function Blood.Pool(pos: Vector3, size: number, growTime: number?, opts: { [string]: any }?): Part?
	local gore = Config.Gore()
	size *= gore.PoolScale
	local ground, normal = groundAt(pos)
	if not ground then
		return nil
	end
	normal = normal or Vector3.yAxis
	-- merge into a nearby pool instead of stacking
	for _, p in poolFolder:GetChildren() do
		if p:IsA("BasePart") and (p.Position - ground).Magnitude < math.max(p.Size.Y, 1) * 0.35 then
			local target = math.min(math.max(p.Size.Y, size) + size * 0.25, 14)
			Util.tween(p, growTime or 2, { Size = Vector3.new(p.Size.X, target, target * (0.85 + math.random() * 0.15)) }, Enum.EasingStyle.Sine)
			p:SetAttribute("Size", target)
			return p
		end
	end
	local p = Instance.new("Part")
	p.Name = "BloodPool"
	p.Shape = Enum.PartType.Cylinder
	p.Anchored = true
	p.CanCollide = false
	p.CanTouch = false
	p.CanQuery = true
	p.CastShadow = false
	p.Material = Enum.Material.SmoothPlastic
	p.Reflectance = 0.14
	p.Color = Config.Blood.Base:Lerp(Config.Blood.Dark, math.random() * 0.35)
	p.Size = Vector3.new(0.05, 0.4, 0.4)
	local right = normal:Cross(Vector3.new(0.3, 0.1, 0.9).Unit)
	if right.Magnitude < 0.1 then
		right = Vector3.xAxis
	end
	p.CFrame = CFrame.fromMatrix(ground + normal * (0.025 + math.random() * 0.01), normal, right.Unit) * CFrame.Angles(math.random() * math.pi * 2, 0, 0)
	p.CollisionGroup = "BloodRay"
	p:SetAttribute("Size", size)
	if opts and opts.victim then
		p:SetAttribute("Victim", opts.victim)
	end
	CollectionService:AddTag(p, "BloodPool")
	p.Parent = poolFolder
	Util.tween(p, growTime or 2.5, { Size = Vector3.new(0.05, size, size * (0.8 + math.random() * 0.2)) }, Enum.EasingStyle.Sine)
	return p
end

-- Mop / clean. Returns true if anything was cleaned.
function Blood.CleanNear(pos: Vector3, radius: number, amount: number): boolean
	local cleaned = false
	for _, p in poolFolder:GetChildren() do
		if p:IsA("BasePart") then
			local d = (Util.flat(p.Position) - Util.flat(pos)).Magnitude
			if d <= radius + p.Size.Y / 2 then
				cleaned = true
				local newSize = p.Size.Y - amount
				if newSize <= 0.6 then
					p:Destroy()
				else
					Util.tween(p, 0.4, { Size = Vector3.new(0.05, newSize, newSize), Transparency = math.min(p.Transparency + 0.08, 0.6) })
					p:SetAttribute("Size", newSize)
				end
			end
		end
	end
	return cleaned
end

function Blood.ClearAll()
	poolFolder:ClearAllChildren()
end

-- ── Wounds (persistent server-side emitters on bodies / stumps) ──────────────
function Blood.Wound(part: BasePart, localCF: CFrame, strength: number, duration: number)
	local a = Instance.new("Attachment")
	a.Name = "Wound"
	a.CFrame = localCF
	a.Parent = part
	local pe = Instance.new("ParticleEmitter")
	pe.Name = "Bleed"
	pe.Color = ColorSequence.new(Config.Blood.Fresh, Config.Blood.Dark)
	pe.LightEmission = 0
	pe.LightInfluence = 1
	pe.Size = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0.14 * strength), NumberSequenceKeypoint.new(1, 0.05) })
	pe.Transparency = NumberSequence.new({ NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(0.8, 0.1), NumberSequenceKeypoint.new(1, 1) })
	pe.Lifetime = NumberRange.new(0.5, 0.9)
	pe.Speed = NumberRange.new(2 * strength, 5 * strength)
	pe.SpreadAngle = Vector2.new(18, 18)
	pe.Acceleration = Vector3.new(0, -50, 0)
	pe.Drag = 0.5
	pe.Rate = 70 * strength * Config.Gore().Droplets
	pe.EmissionDirection = Enum.NormalId.Front
	pe.Parent = a
	-- pulse with a dying heartbeat, then fade to a drip
	task.spawn(function()
		local t0 = os.clock()
		while a.Parent and os.clock() - t0 < duration do
			local life = 1 - (os.clock() - t0) / duration
			local beat = (math.sin((os.clock() - t0) * 9) + 1) / 2
			pe.Rate = (10 + 90 * beat ^ 6) * strength * life * Config.Gore().Droplets
			task.wait(0.05)
		end
		if pe.Parent then
			pe.Rate = 3
			task.wait(20)
			if pe.Parent then
				pe.Enabled = false
			end
		end
	end)
	return a
end

-- ── Stained clothes ──────────────────────────────────────────────────────────
local STAIN_PARTS = { "UpperTorso", "LowerTorso", "RightUpperArm", "RightLowerArm", "RightHand", "LeftLowerArm", "LeftHand", "RightUpperLeg", "LeftUpperLeg" }

function Blood.Stain(character: Model, count: number)
	local folder = character:FindFirstChild("Stains")
	if not folder then
		folder = Instance.new("Folder")
		folder.Name = "Stains"
		folder.Parent = character
	end
	if #folder:GetChildren() > 46 then
		return
	end
	for _ = 1, count do
		local name = STAIN_PARTS[math.random(1, #STAIN_PARTS)]
		local bone = character:FindFirstChild(name) :: BasePart?
		if bone then
			local s = bone.Size
			local front = math.random() < 0.8
			local offset = Vector3.new((math.random() - 0.5) * s.X * 0.85, (math.random() - 0.5) * s.Y * 0.85, (front and -1 or 1) * (s.Z / 2 + 0.01))
			local d = 0.18 + math.random() * 0.42
			local cf = bone.CFrame * CFrame.new(offset) * CFrame.Angles(0, 0, math.random() * math.pi)
			local p = Util.deco(Vector3.new(d * (0.7 + math.random() * 0.6), d, 0.03), cf, Config.Blood.Base:Lerp(Config.Blood.Dark, math.random() * 0.5), folder, "Stain")
			Util.mesh(p, Enum.MeshType.Sphere)
			Util.weld(bone, p)
		end
	end
end

function Blood.ClearStains(character: Model)
	local folder = character:FindFirstChild("Stains")
	if folder then
		folder:ClearAllChildren()
	end
end

-- ── Weapons ──────────────────────────────────────────────────────────────────
function Blood.SetWeaponBloody(tool: Tool, bloody: boolean)
	tool:SetAttribute("Bloody", bloody)
	for _, p in tool:GetDescendants() do
		if p:IsA("BasePart") and p:GetAttribute("Blade") then
			local orig = p:GetAttribute("OrigColor")
			if orig == nil then
				p:SetAttribute("OrigColor", p.Color)
				orig = p.Color
			end
			p.Color = bloody and (orig :: Color3):Lerp(Config.Blood.Base, 0.75) or orig
			p.Reflectance = bloody and 0.1 or p.Reflectance
			local drip = p:FindFirstChild("Drip")
			if bloody and not drip then
				local pe = Instance.new("ParticleEmitter")
				pe.Name = "Drip"
				pe.Color = ColorSequence.new(Config.Blood.Base)
				pe.Size = NumberSequence.new(0.07)
				pe.Lifetime = NumberRange.new(0.5, 0.8)
				pe.Rate = 3
				pe.Speed = NumberRange.new(0, 0.2)
				pe.Acceleration = Vector3.new(0, -40, 0)
				pe.Parent = p
			elseif not bloody and drip then
				drip:Destroy()
			end
		end
	end
end

return Blood

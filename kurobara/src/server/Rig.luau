-- KUROBARA · Rigs: building characters, faces, hair, uniforms, ragdolls, dismemberment, speech
-- Uses standard R15 rigs so Roblox's default walk/idle animations work, then dresses them
-- with welded parts: an anime-style head, 3D eyes that change expression, hair, uniform.

local Players = game:GetService("Players")
local CollectionService = game:GetService("CollectionService")

local Shared = game:GetService("ReplicatedStorage"):WaitForChild("Shared")
local Util = require(Shared.Util)
local Config = require(Shared.Config)

local Rig = {}

local UNIFORM = {
	Blazer = Color3.fromRGB(34, 30, 42),
	Skirt = Color3.fromRGB(110, 20, 38),
	Pants = Color3.fromRGB(38, 36, 46),
	Shirt = Color3.fromRGB(246, 246, 250),
	Ribbon = Color3.fromRGB(205, 30, 62),
	Shoes = Color3.fromRGB(30, 24, 22),
	Socks = Color3.fromRGB(238, 238, 242),
	TeacherF = Color3.fromRGB(64, 62, 80),
	TeacherM = Color3.fromRGB(52, 54, 62),
}

local SKIN_TONES = {
	Color3.fromRGB(255, 226, 210),
	Color3.fromRGB(250, 214, 190),
	Color3.fromRGB(236, 196, 166),
	Color3.fromRGB(214, 168, 130),
	Color3.fromRGB(180, 128, 96),
}

-- Visual head (ellipsoid) semi-axes
local HA, HB, HC = 0.65, 0.68, 0.62

local function lookFor(def)
	local look = table.clone(def)
	look.skin = look.skin or SKIN_TONES[(#(def.name or "x") % #SKIN_TONES) + 1]
	look.height = look.height or (0.94 + ((#(def.name or "") * 7) % 10) / 100)
	return look
end
Rig.LookFor = lookFor

function Rig.Description(look): HumanoidDescription
	local d = Instance.new("HumanoidDescription")
	local skin = look.skin
	d.HeadColor = skin
	d.TorsoColor = UNIFORM.Blazer
	d.LeftArmColor = UNIFORM.Blazer
	d.RightArmColor = UNIFORM.Blazer
	d.LeftLegColor = skin
	d.RightLegColor = skin
	d.HeightScale = look.height or 1
	d.WidthScale = look.gender == "F" and 0.86 or 0.96
	d.DepthScale = look.gender == "F" and 0.9 or 1
	d.HeadScale = 1
	d.BodyTypeScale = 0
	d.ProportionScale = 0
	return d
end

-- ── Dressing ─────────────────────────────────────────────────────────────────
local function addDeco(model: Model, bone: BasePart, size: Vector3, cf: CFrame, color: Color3, name: string, mat: Enum.Material?, sphere: boolean?): Part
	local look = model:FindFirstChild("Look")
	if not look then
		look = Instance.new("Folder")
		look.Name = "Look"
		look.Parent = model
	end
	local p = Util.deco(size, cf, color, look, name, mat)
	if sphere then
		Util.mesh(p, Enum.MeshType.Sphere)
	end
	p:SetAttribute("Bone", bone.Name)
	Util.weld(bone, p)
	return p
end

local function surfaceZ(x: number, y: number): number
	local k = 1 - (x / HA) ^ 2 - (y / HB) ^ 2
	return -HC * math.sqrt(math.max(k, 0.02))
end

-- local CFrame on the head ellipsoid surface at (x, y), facing outward, pushed out by `out`
local function onFace(x: number, y: number, out: number): CFrame
	local z = surfaceZ(x, y)
	local n = Vector3.new(x / (HA * HA), y / (HB * HB), z / (HC * HC)).Unit
	local pos = Vector3.new(x, y, z) + n * out
	return CFrame.lookAt(pos, pos + n)
end

local function buildFace(model: Model, head: BasePart, fh: BasePart, look)
	local function at(x, y, out)
		return fh.CFrame * onFace(x, y, out)
	end
	local eyeColor = look.eyeColor or Color3.fromRGB(80, 60, 50)
	for _, side in { -1, 1 } do
		local tag = side < 0 and "L" or "R"
		local x, y = 0.25 * side, -0.06
		addDeco(model, head, Vector3.new(0.3, 0.37, 0.05), at(x, y, -0.005), Color3.fromRGB(252, 252, 255), "Eye" .. tag .. "_Sclera", nil, true)
		addDeco(model, head, Vector3.new(0.23, 0.31, 0.05), at(x, y - 0.02, 0.008), eyeColor, "Eye" .. tag .. "_Iris", nil, true)
		addDeco(model, head, Vector3.new(0.11, 0.17, 0.05), at(x, y - 0.03, 0.018), Color3.fromRGB(16, 12, 18), "Eye" .. tag .. "_Pupil", nil, true)
		addDeco(model, head, Vector3.new(0.065, 0.065, 0.05), at(x - 0.05 * side, y + 0.06, 0.028), Color3.fromRGB(255, 255, 255), "Eye" .. tag .. "_Shine", Enum.Material.Neon, true)
		addDeco(model, head, Vector3.new(0.36, 0.05, 0.05), at(x + 0.01 * side, y + 0.19, 0.01), Color3.fromRGB(20, 16, 22), "Eye" .. tag .. "_Lash")
		local lid = addDeco(model, head, Vector3.new(0.3, 0.045, 0.05), at(x, y - 0.02, 0.03), Color3.fromRGB(20, 16, 22), "Eye" .. tag .. "_Lid")
		lid.Transparency = 1
		local blush = addDeco(model, head, Vector3.new(0.22, 0.08, 0.04), at(0.37 * side, -0.22, 0.01), Color3.fromRGB(255, 120, 150), "Blush" .. tag, nil, true)
		blush.Transparency = 1
	end
	addDeco(model, head, Vector3.new(0.16, 0.035, 0.04), at(0, -0.34, 0.0), Color3.fromRGB(110, 40, 52), "Mouth")
	addDeco(model, head, Vector3.new(0.05, 0.08, 0.05), at(0, -0.17, -0.005), look.skin:Lerp(Color3.new(0, 0, 0), 0.08), "Nose", nil, true)
	if look.glasses then
		for _, side in { -1, 1 } do
			local lens = addDeco(model, head, Vector3.new(0.36, 0.3, 0.02), at(0.25 * side, -0.06, 0.09), Color3.fromRGB(200, 220, 240), "Lens", Enum.Material.Glass)
			lens.Transparency = 0.8
			addDeco(model, head, Vector3.new(0.38, 0.035, 0.03), at(0.25 * side, 0.1, 0.1), Color3.fromRGB(20, 20, 24), "Frame")
			addDeco(model, head, Vector3.new(0.38, 0.035, 0.03), at(0.25 * side, -0.22, 0.1), Color3.fromRGB(20, 20, 24), "Frame")
		end
		addDeco(model, head, Vector3.new(0.14, 0.035, 0.03), at(0, 0.0, 0.1), Color3.fromRGB(20, 20, 24), "Bridge")
	end
end

local HairStyles = {}

local function cap(model, head, fh, color, scale)
	scale = scale or 1
	addDeco(model, head, Vector3.new(1.44, 1.2, 1.38) * scale, fh.CFrame * CFrame.new(0, 0.24, 0.06), color, "HairCap", nil, true)
	for i, x in { -0.32, -0.08, 0.16, 0.36 } do
		local cf = fh.CFrame * onFace(x, 0.28, 0.02) * CFrame.Angles(0, 0, math.rad((i % 2 == 0) and 12 or -10))
		addDeco(model, head, Vector3.new(0.3, 0.42, 0.14), cf, color, "Bang", nil, true)
	end
end

function HairStyles.Short(model, head, fh, look)
	cap(model, head, fh, look.hairColor, 0.98)
	for _, s in { -1, 1 } do
		addDeco(model, head, Vector3.new(0.14, 0.42, 0.3), fh.CFrame * CFrame.new(0.64 * s, -0.12, -0.05), look.hairColor, "Sideburn", nil, true)
	end
end

function HairStyles.Spiky(model, head, fh, look)
	cap(model, head, fh, look.hairColor)
	for i = 1, 7 do
		local a = (i / 7) * math.pi * 2
		local cf = fh.CFrame * CFrame.new(math.cos(a) * 0.42, 0.62, math.sin(a) * 0.38 + 0.08) * CFrame.Angles(math.sin(a) * 0.6, 0, -math.cos(a) * 0.6)
		addDeco(model, head, Vector3.new(0.3, 0.7, 0.3), cf, look.hairColor, "Spike", nil, true)
	end
end

function HairStyles.Messy(model, head, fh, look)
	cap(model, head, fh, look.hairColor, 1.02)
	for i = 1, 9 do
		local a = i * 2.4
		local cf = fh.CFrame * CFrame.new(math.cos(a) * 0.5, 0.45 + (i % 3) * 0.08, math.sin(a) * 0.45 + 0.1) * CFrame.Angles(i * 0.3, i, i * 0.2)
		addDeco(model, head, Vector3.new(0.38, 0.32, 0.38), cf, look.hairColor, "Tuft", nil, true)
	end
	addDeco(model, head, Vector3.new(1.3, 0.6, 0.4), fh.CFrame * CFrame.new(0, -0.15, 0.52), look.hairColor, "Nape", nil, true)
end

function HairStyles.Long(model, head, fh, look)
	cap(model, head, fh, look.hairColor)
	addDeco(model, head, Vector3.new(1.32, 2.2, 0.42), fh.CFrame * CFrame.new(0, -0.62, 0.48), look.hairColor, "HairBack", nil, true)
	for _, s in { -1, 1 } do
		addDeco(model, head, Vector3.new(0.26, 1.5, 0.42), fh.CFrame * CFrame.new(0.62 * s, -0.42, 0.02), look.hairColor, "SideLock", nil, true)
	end
end

function HairStyles.SideLong(model, head, fh, look)
	HairStyles.Long(model, head, fh, look)
	local tail = addDeco(model, head, Vector3.new(0.5, 1.7, 0.5), fh.CFrame * CFrame.new(0.82, -0.62, 0.18) * CFrame.Angles(0, 0, math.rad(-8)), look.hairColor, "SideTail", nil, true)
	addDeco(model, head, Vector3.new(0.36, 0.5, 0.36), tail.CFrame * CFrame.new(0, -0.8, 0), look.hairTip or look.hairColor, "SideTailTip", nil, true)
	addDeco(model, head, Vector3.new(1.2, 0.4, 0.38), fh.CFrame * CFrame.new(0, -1.6, 0.5), look.hairTip or look.hairColor, "HairTip", nil, true)
	addDeco(model, head, Vector3.new(0.22, 0.22, 0.22), fh.CFrame * CFrame.new(0.66, 0.05, 0.12), Config.UI.Accent, "HairClip", Enum.Material.Neon, true)
end

function HairStyles.Ponytail(model, head, fh, look)
	cap(model, head, fh, look.hairColor)
	addDeco(model, head, Vector3.new(0.22, 0.22, 0.22), fh.CFrame * CFrame.new(0, 0.18, 0.7), UNIFORM.Ribbon, "HairTie", nil, true)
	addDeco(model, head, Vector3.new(0.5, 1.5, 0.5), fh.CFrame * CFrame.new(0, -0.45, 0.86) * CFrame.Angles(math.rad(18), 0, 0), look.hairColor, "Ponytail", nil, true)
end

function HairStyles.Twintails(model, head, fh, look)
	cap(model, head, fh, look.hairColor)
	for _, s in { -1, 1 } do
		addDeco(model, head, Vector3.new(0.52, 1.8, 0.52), fh.CFrame * CFrame.new(0.78 * s, -0.55, 0.25) * CFrame.Angles(0, 0, math.rad(10 * s)), look.hairColor, "Twintail", nil, true)
		addDeco(model, head, Vector3.new(0.34, 0.24, 0.24), fh.CFrame * CFrame.new(0.66 * s, 0.32, 0.22), UNIFORM.Ribbon, "Ribbon", nil, true)
	end
end

function HairStyles.Bob(model, head, fh, look)
	cap(model, head, fh, look.hairColor)
	for _, s in { -1, 1 } do
		addDeco(model, head, Vector3.new(0.34, 1.05, 1.05), fh.CFrame * CFrame.new(0.62 * s, -0.2, 0.12), look.hairColor, "BobSide", nil, true)
	end
	addDeco(model, head, Vector3.new(1.34, 1.0, 0.4), fh.CFrame * CFrame.new(0, -0.22, 0.56), look.hairColor, "BobBack", nil, true)
end

function HairStyles.Bun(model, head, fh, look)
	cap(model, head, fh, look.hairColor)
	addDeco(model, head, Vector3.new(0.68, 0.62, 0.62), fh.CFrame * CFrame.new(0, 0.62, 0.48), look.hairColor, "Bun", nil, true)
end

function Rig.Dress(model: Model, look, role: string?)
	role = role or "Student"
	local function part(name: string): BasePart?
		return model:FindFirstChild(name) :: BasePart?
	end
	local head = part("Head")
	local ut = part("UpperTorso")
	local lt = part("LowerTorso")
	if not (head and ut and lt) then
		warn("[Kurobara] Rig.Dress: model missing R15 parts", model:GetFullName())
		return
	end

	local bc = model:FindFirstChildOfClass("BodyColors")
	if bc then
		bc:Destroy()
	end
	local female = look.gender == "F"
	local top = UNIFORM.Blazer
	local bottom = female and UNIFORM.Skirt or UNIFORM.Pants
	if role == "Teacher" then
		top = female and UNIFORM.TeacherF or UNIFORM.TeacherM
		bottom = top
	end
	local socks = look.socks or UNIFORM.Socks
	local colors = {
		UpperTorso = top, LowerTorso = bottom,
		LeftUpperArm = top, RightUpperArm = top, LeftLowerArm = top, RightLowerArm = top,
		LeftHand = look.skin, RightHand = look.skin,
		LeftUpperLeg = female and look.skin or bottom, RightUpperLeg = female and look.skin or bottom,
		LeftLowerLeg = female and socks or bottom, RightLowerLeg = female and socks or bottom,
		LeftFoot = UNIFORM.Shoes, RightFoot = UNIFORM.Shoes,
	}
	for name, c in colors do
		local p = part(name)
		if p then
			p.Color = c
			p.Material = Enum.Material.SmoothPlastic
		end
	end

	-- hide the stock head + face, add our own
	head.Transparency = 1
	for _, d in head:GetChildren() do
		if d:IsA("Decal") then
			d.Transparency = 1
		end
	end
	local fh = addDeco(model, head, Vector3.new(HA * 2, HB * 2, HC * 2), head.CFrame * CFrame.new(0, 0.05, 0), look.skin, "FaceHead", nil, true)
	addDeco(model, head, Vector3.new(0.5, 0.6, 0.5), head.CFrame * CFrame.new(0, -0.6, 0.05), look.skin, "Neck", nil, true)
	buildFace(model, head, fh, look)
	local style = HairStyles[look.hair or "Short"] or HairStyles.Short
	style(model, head, fh, look)

	-- uniform details
	local us = ut.Size
	addDeco(model, ut, Vector3.new(us.X * 0.55, 0.2, us.Z * 0.55), ut.CFrame * CFrame.new(0, us.Y / 2 - 0.06, -0.05), UNIFORM.Shirt, "Collar")
	if role == "Teacher" then
		addDeco(model, ut, Vector3.new(0.5, us.Y * 0.7, 0.06), ut.CFrame * CFrame.new(0, us.Y * 0.1, -us.Z / 2 - 0.02), UNIFORM.Shirt, "ShirtFront")
	end
	if female and role ~= "Teacher" then
		addDeco(model, ut, Vector3.new(0.46, 0.26, 0.08), ut.CFrame * CFrame.new(0, us.Y / 2 - 0.36, -us.Z / 2 - 0.03), UNIFORM.Ribbon, "Ribbon")
	else
		addDeco(model, ut, Vector3.new(0.2, us.Y * 0.62, 0.06), ut.CFrame * CFrame.new(0, us.Y / 2 - us.Y * 0.36, -us.Z / 2 - 0.03), UNIFORM.Ribbon, "Tie")
	end
	-- crimson academy crest
	if role ~= "Teacher" then
		addDeco(model, ut, Vector3.new(0.22, 0.22, 0.05), ut.CFrame * CFrame.new(us.X * 0.26, us.Y * 0.18, -us.Z / 2 - 0.02), UNIFORM.Ribbon, "Crest", Enum.Material.Neon, true)
	end
	if female then
		local ls = lt.Size
		addDeco(model, lt, Vector3.new(ls.X * 1.12, 1.25, ls.Z * 1.32), lt.CFrame * CFrame.new(0, -0.42, 0), bottom, "Skirt")
		addDeco(model, lt, Vector3.new(ls.X * 1.14, 0.12, ls.Z * 1.34), lt.CFrame * CFrame.new(0, -0.98, 0), bottom:Lerp(Color3.new(0, 0, 0), 0.35), "SkirtHem")
	end

	model:SetAttribute("EyeColor", look.eyeColor)
	model:SetAttribute("Expression", "Neutral")
end

-- ── Expressions ──────────────────────────────────────────────────────────────
local function face(model: Model, name: string): BasePart?
	local look = model:FindFirstChild("Look")
	return look and look:FindFirstChild(name) :: BasePart?
end

function Rig.SetExpression(model: Model, expr: string)
	if model:GetAttribute("Expression") == expr then
		return
	end
	model:SetAttribute("Expression", expr)
	local eyeColor: Color3 = model:GetAttribute("EyeColor") or Color3.fromRGB(80, 60, 50)
	local pupil = Vector3.new(0.11, 0.17, 0.05)
	local iris = Vector3.new(0.23, 0.31, 0.05)
	local irisMat = Enum.Material.SmoothPlastic
	local irisCol = eyeColor
	local mouthSize = Vector3.new(0.16, 0.035, 0.04)
	local mouthCol = Color3.fromRGB(110, 40, 52)
	local eyesOpen = true
	if expr == "Smile" then
		mouthSize = Vector3.new(0.24, 0.05, 0.04)
		mouthCol = Color3.fromRGB(170, 60, 80)
	elseif expr == "Psycho" then
		pupil = Vector3.new(0.035, 0.06, 0.05)
		iris = Vector3.new(0.18, 0.24, 0.05)
		irisMat = Enum.Material.Neon
		irisCol = Color3.fromRGB(255, 20, 50)
		mouthSize = Vector3.new(0.34, 0.1, 0.04)
		mouthCol = Color3.fromRGB(40, 4, 10)
	elseif expr == "Scared" then
		pupil = Vector3.new(0.05, 0.07, 0.05)
		mouthSize = Vector3.new(0.13, 0.16, 0.04)
		mouthCol = Color3.fromRGB(50, 10, 20)
	elseif expr == "Dead" then
		eyesOpen = false
		mouthSize = Vector3.new(0.12, 0.08, 0.04)
		mouthCol = Color3.fromRGB(40, 10, 16)
	end
	for _, s in { "L", "R" } do
		local p = face(model, "Eye" .. s .. "_Pupil")
		local i = face(model, "Eye" .. s .. "_Iris")
		local sc = face(model, "Eye" .. s .. "_Sclera")
		local sh = face(model, "Eye" .. s .. "_Shine")
		local lid = face(model, "Eye" .. s .. "_Lid")
		if p then
			p.Size = pupil
			p.Transparency = eyesOpen and 0 or 1
		end
		if i then
			i.Size = iris
			i.Color = irisCol
			i.Material = irisMat
			i.Transparency = eyesOpen and 0 or 1
		end
		if sc then
			sc.Transparency = eyesOpen and 0 or 1
		end
		if sh then
			sh.Transparency = (eyesOpen and expr ~= "Psycho") and 0 or 1
		end
		if lid then
			lid.Transparency = eyesOpen and 1 or 0
		end
	end
	local m = face(model, "Mouth")
	if m then
		m.Size = mouthSize
		m.Color = mouthCol
	end
end

function Rig.SetBlush(model: Model, on: boolean)
	for _, s in { "L", "R" } do
		local b = face(model, "Blush" .. s)
		if b then
			b.Transparency = on and 0.45 or 1
		end
	end
end

-- ── Creating rigs ────────────────────────────────────────────────────────────
function Rig.CreateNPC(def, role: string?): Model
	local look = lookFor(def)
	local desc = Rig.Description(look)
	local model = Players:CreateHumanoidModelFromDescription(desc, Enum.HumanoidRigType.R15)
	model.Name = def.name
	local hum = model:FindFirstChildOfClass("Humanoid") :: Humanoid
	hum.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None
	hum.HealthDisplayType = Enum.HumanoidHealthDisplayType.AlwaysOff
	hum.BreakJointsOnDeath = false
	hum.RequiresNeck = false
	hum.UseJumpPower = true
	hum.JumpPower = 0
	hum.WalkSpeed = Config.NPC.WalkSpeed
	local animate = model:FindFirstChild("Animate")
	if animate then
		animate:Destroy()
	end
	if not hum:FindFirstChildOfClass("Animator") then
		Instance.new("Animator").Parent = hum
	end
	Rig.Dress(model, look, role)
	for _, p in model:GetDescendants() do
		if p:IsA("BasePart") and p.Parent == model then
			p.CollisionGroup = "NPC"
		end
	end
	model:SetAttribute("NPCId", def.id)
	model:SetAttribute("DisplayName", def.name)
	return model
end

-- Server-side default animations for NPCs (Roblox's stock R15 set)
local ANIM = {
	Idle = "rbxassetid://507766388",
	Walk = "rbxassetid://507777826",
	Run = "rbxassetid://507767714",
	Sit = "rbxassetid://2506281703",
}

function Rig.AnimateNPC(model: Model)
	local hum = model:FindFirstChildOfClass("Humanoid")
	local animator = hum and hum:FindFirstChildOfClass("Animator")
	if not (hum and animator) then
		return
	end
	local tracks = {}
	for name, id in ANIM do
		local a = Instance.new("Animation")
		a.AnimationId = id
		local ok, track = pcall(function()
			return animator:LoadAnimation(a)
		end)
		if ok and track then
			track.Priority = name == "Sit" and Enum.AnimationPriority.Action or Enum.AnimationPriority.Core
			tracks[name] = track
		end
	end
	local current: AnimationTrack? = nil
	local function play(name: string, speed: number?)
		local t = tracks[name]
		if not t then
			return
		end
		if current ~= t then
			if current then
				current:Stop(0.2)
			end
			t:Play(0.2)
			current = t
		end
		if speed then
			t:AdjustSpeed(speed)
		end
	end
	local conns = {}
	table.insert(conns, hum.Running:Connect(function(speed)
		if model:GetAttribute("Sitting") then
			return
		end
		if speed > 13 then
			play("Run", speed / 16)
		elseif speed > 0.6 then
			play("Walk", speed / 10)
		else
			play("Idle")
		end
	end))
	table.insert(conns, model:GetAttributeChangedSignal("Sitting"):Connect(function()
		if model:GetAttribute("Sitting") then
			play("Sit")
		else
			play("Idle")
		end
	end))
	table.insert(conns, hum.Died:Connect(function()
		for _, t in tracks do
			t:Stop(0)
		end
		for _, c in conns do
			c:Disconnect()
		end
	end))
	play("Idle")
	return function()
		for _, t in tracks do
			t:Stop(0)
		end
		for _, c in conns do
			c:Disconnect()
		end
	end
end

-- ── Ragdoll & dismemberment ──────────────────────────────────────────────────
local LIMBS = {
	Head = { joint = "Neck", parts = { "Head" } },
	LeftArm = { joint = "LeftShoulder", parts = { "LeftUpperArm", "LeftLowerArm", "LeftHand" } },
	RightArm = { joint = "RightShoulder", parts = { "RightUpperArm", "RightLowerArm", "RightHand" } },
	LeftLeg = { joint = "LeftHip", parts = { "LeftUpperLeg", "LeftLowerLeg", "LeftFoot" } },
	RightLeg = { joint = "RightHip", parts = { "RightUpperLeg", "RightLowerLeg", "RightFoot" } },
}
Rig.Limbs = LIMBS

function Rig.Ragdoll(model: Model)
	if model:GetAttribute("Ragdolled") then
		return
	end
	model:SetAttribute("Ragdolled", true)
	local hum = model:FindFirstChildOfClass("Humanoid")
	if hum then
		hum.BreakJointsOnDeath = false
		hum.RequiresNeck = false
		hum.PlatformStand = true
		hum.AutoRotate = false
		hum:ChangeState(Enum.HumanoidStateType.Physics)
	end
	for _, m in model:GetDescendants() do
		if m:IsA("Motor6D") and m.Name ~= "Root" and m.Part0 and m.Part1 then
			local a0 = Instance.new("Attachment")
			a0.Name = "RagA0_" .. m.Name
			a0.CFrame = m.C0
			a0.Parent = m.Part0
			local a1 = Instance.new("Attachment")
			a1.Name = "RagA1_" .. m.Name
			a1.CFrame = m.C1
			a1.Parent = m.Part1
			local bs = Instance.new("BallSocketConstraint")
			bs.Name = "Rag_" .. m.Name
			bs.Attachment0 = a0
			bs.Attachment1 = a1
			bs.LimitsEnabled = true
			bs.TwistLimitsEnabled = true
			local big = m.Name:find("Shoulder") or m.Name:find("Hip")
			bs.UpperAngle = big and 70 or (m.Name == "Neck" and 40 or 45)
			bs.TwistLowerAngle = -40
			bs.TwistUpperAngle = 40
			bs.Parent = m.Parent
			m.Enabled = false
		end
	end
	local hrp = model:FindFirstChild("HumanoidRootPart") :: BasePart?
	for _, p in model:GetChildren() do
		if p:IsA("BasePart") then
			p.Anchored = false
			p.CollisionGroup = "Ragdoll"
			p.CanCollide = p ~= hrp
		end
	end
	if hrp then
		hrp.CanCollide = false
		hrp.Massless = true
		hrp.Transparency = 1
	end
	for _, p in model:GetChildren() do
		if p:IsA("BasePart") and not p.Anchored then
			pcall(function()
				p:SetNetworkOwner(nil)
			end)
		end
	end
end

-- Detach a limb (after ragdolling). Returns the severed model.
function Rig.Dismember(model: Model, limb: string): Model?
	local info = LIMBS[limb]
	if not info or model:GetAttribute("Severed_" .. limb) then
		return nil
	end
	local first = model:FindFirstChild(info.parts[1]) :: BasePart?
	if not first then
		return nil
	end
	model:SetAttribute("Severed_" .. limb, true)
	local motor = first:FindFirstChild(info.joint)
	if motor and motor:IsA("Motor6D") then
		motor.Enabled = false
	end
	local bs = first:FindFirstChild("Rag_" .. info.joint)
	if bs then
		bs:Destroy()
	end
	local sev = Instance.new("Model")
	sev.Name = (model:GetAttribute("DisplayName") or model.Name) .. "'s " .. limb
	local partSet = {}
	for _, n in info.parts do
		partSet[n] = true
	end
	for _, n in info.parts do
		local p = model:FindFirstChild(n)
		if p then
			p.Parent = sev
		end
	end
	local look = model:FindFirstChild("Look")
	if look then
		for _, d in look:GetChildren() do
			if partSet[d:GetAttribute("Bone") or ""] then
				d.Parent = sev
			end
		end
	end
	sev.PrimaryPart = first
	sev:SetAttribute("Victim", model:GetAttribute("DisplayName") or model.Name)
	sev:SetAttribute("Limb", limb)
	sev.Parent = model.Parent
	CollectionService:AddTag(sev, "BodyPart")
	for _, p in sev:GetDescendants() do
		if p:IsA("BasePart") then
			p.CollisionGroup = "Ragdoll"
			if not p:GetAttribute("Bone") then
				p.CanCollide = true
			end
		end
	end
	-- a little kick so it flops away
	first.AssemblyLinearVelocity = Vector3.new(math.random(-8, 8), 10, math.random(-8, 8))
	pcall(function()
		first:SetNetworkOwner(nil)
	end)
	return sev
end

-- ── Speech & alerts ──────────────────────────────────────────────────────────
local function anchorPart(model: Model): BasePart?
	local look = model:FindFirstChild("Look")
	local fh = look and look:FindFirstChild("FaceHead")
	return (fh or model:FindFirstChild("Head")) :: BasePart?
end

function Rig.Say(model: Model, text: string, color: Color3?, duration: number?)
	local adornee = anchorPart(model)
	if not adornee then
		return
	end
	local old = model:FindFirstChild("Speech")
	if old then
		old:Destroy()
	end
	local bb = Instance.new("BillboardGui")
	bb.Name = "Speech"
	bb.Adornee = adornee
	bb.Size = UDim2.fromOffset(260, 64)
	bb.StudsOffset = Vector3.new(0, 2.4, 0)
	bb.MaxDistance = 70
	bb.LightInfluence = 0
	bb.AlwaysOnTop = false
	local frame = Instance.new("Frame")
	frame.AnchorPoint = Vector2.new(0.5, 1)
	frame.Position = UDim2.fromScale(0.5, 1)
	frame.Size = UDim2.fromScale(1, 1)
	frame.AutomaticSize = Enum.AutomaticSize.None
	frame.BackgroundColor3 = Color3.fromRGB(20, 18, 26)
	frame.BackgroundTransparency = 0.25
	frame.Parent = bb
	local corner = Instance.new("UICorner")
	corner.CornerRadius = UDim.new(0, 18)
	corner.Parent = frame
	local stroke = Instance.new("UIStroke")
	stroke.Color = Color3.fromRGB(255, 255, 255)
	stroke.Transparency = 0.7
	stroke.Thickness = 1.2
	stroke.Parent = frame
	local label = Instance.new("TextLabel")
	label.BackgroundTransparency = 1
	label.Size = UDim2.new(1, -20, 1, -10)
	label.Position = UDim2.fromOffset(10, 5)
	label.TextWrapped = true
	label.TextScaled = true
	label.Text = text
	label.TextColor3 = color or Color3.fromRGB(245, 245, 250)
	label.FontFace = Font.new(Config.UI.Font, Enum.FontWeight.SemiBold)
	label.Parent = frame
	local size = Instance.new("UITextSizeConstraint")
	size.MaxTextSize = 20
	size.Parent = label
	bb.Parent = model
	task.delay(duration or 3.2, function()
		if bb.Parent then
			bb:Destroy()
		end
	end)
end

function Rig.Alert(model: Model, symbol: string, color: Color3?)
	local adornee = anchorPart(model)
	if not adornee then
		return
	end
	local old = model:FindFirstChild("Alert")
	if old then
		old:Destroy()
	end
	local bb = Instance.new("BillboardGui")
	bb.Name = "Alert"
	bb.Adornee = adornee
	bb.Size = UDim2.fromOffset(60, 60)
	bb.StudsOffset = Vector3.new(0, 3.7, 0)
	bb.MaxDistance = 90
	bb.LightInfluence = 0
	bb.AlwaysOnTop = true
	local t = Instance.new("TextLabel")
	t.BackgroundTransparency = 1
	t.Size = UDim2.fromScale(1, 1)
	t.Text = symbol
	t.TextScaled = true
	t.TextColor3 = color or Config.UI.Warn
	t.FontFace = Font.new(Config.UI.Font, Enum.FontWeight.Heavy)
	t.TextStrokeTransparency = 0.4
	t.Parent = bb
	bb.Parent = model
	task.delay(2.2, function()
		if bb.Parent then
			bb:Destroy()
		end
	end)
end

return Rig

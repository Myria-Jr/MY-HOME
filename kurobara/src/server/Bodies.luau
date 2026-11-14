-- KUROBARA · Bodies & evidence handling: drag, carry, chop, bag, burn, bury, mop

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Players = game:GetService("Players")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Util = require(Shared.Util)

local Bodies = {}
local S

function Bodies.Init(services)
	S = services
end

local function prompt(parent: Instance, name: string, action: string, object: string, key: Enum.KeyCode, hold: number?): ProximityPrompt
	local old = parent:FindFirstChild(name)
	if old then
		old:Destroy()
	end
	local p = Instance.new("ProximityPrompt")
	p.Name = name
	p.ActionText = action
	p.ObjectText = object
	p.KeyboardKeyCode = key
	p.HoldDuration = hold or 0
	p.MaxActivationDistance = 7
	p.RequiresLineOfSight = false
	p.Style = Enum.ProximityPromptStyle.Custom
	p.Parent = parent
	return p
end

local function setOwner(model: Model, owner: Player?)
	for _, p in model:GetDescendants() do
		if p:IsA("BasePart") and not p.Anchored then
			pcall(function()
				p:SetNetworkOwner(owner)
			end)
		end
	end
end

local function victimName(model: Model): string
	return model:GetAttribute("Victim") or model:GetAttribute("DisplayName") or model.Name
end

-- ── Setup ────────────────────────────────────────────────────────────────────
function Bodies.Setup(body: Model)
	CollectionService:AddTag(body, "Body")
	body:SetAttribute("DeathTime", workspace:GetServerTimeNow())
	Bodies.RefreshPrompts(body)
end

function Bodies.RefreshPrompts(body: Model)
	local torso = body:FindFirstChild("UpperTorso") :: BasePart?
	if not torso then
		return
	end
	local name = victimName(body)
	local drag = prompt(torso, "DragPrompt", "Drag", name, Enum.KeyCode.E)
	drag.Triggered:Connect(function(player)
		local st = S.GameState.Get(player)
		if not st then
			return
		end
		if st.Dragging == body then
			Bodies.StopDrag(player)
		else
			Bodies.StartDrag(player, body)
		end
	end)
	if body:GetAttribute("Chopped") then
		local bag = prompt(torso, "BagPrompt", "Bag it", name, Enum.KeyCode.F, 0.6)
		bag.Triggered:Connect(function(player)
			Bodies.Bag(player, body)
		end)
		local d = torso:FindFirstChild("ChopPrompt")
		if d then
			d:Destroy()
		end
	elseif Config.Gore().Dismember then
		local chop = prompt(torso, "ChopPrompt", "Dismember", "needs Fire Axe", Enum.KeyCode.F, 0.4)
		chop.Triggered:Connect(function(player)
			S.Combat.DismemberBody(player, body)
		end)
	end
end

function Bodies.SetupPart(sev: Model)
	local root = sev.PrimaryPart
	if not root then
		return
	end
	local name = victimName(sev) .. " · " .. (sev:GetAttribute("Limb") or "part")
	local pick = prompt(root, "CarryPrompt", "Pick up", name, Enum.KeyCode.E)
	pick.Triggered:Connect(function(player)
		Bodies.StartCarry(player, sev, "Part")
	end)
	local bag = prompt(root, "BagPrompt", "Bag it", name, Enum.KeyCode.F, 0.4)
	bag.Triggered:Connect(function(player)
		Bodies.Bag(player, sev)
	end)
end

-- Cut a limb off a ragdolled body
function Bodies.Sever(body: Model, limb: string): Model?
	local sev = S.Rig.Dismember(body, limb)
	if not sev then
		return nil
	end
	local torso = body:FindFirstChild("UpperTorso") :: BasePart?
	local root = sev.PrimaryPart
	if torso and root then
		local jointPos = root.Position
		S.Blood.FX("Burst", { pos = jointPos, power = 1.2 })
		if Config.Gore().Arterial then
			S.Blood.FX("Arterial", { part = torso, offset = torso.CFrame:ToObjectSpace(CFrame.new(jointPos)), dir = Vector3.new(0, 0.6, -1), duration = 3, power = 0.8 })
		end
		S.Blood.Wound(torso, torso.CFrame:ToObjectSpace(CFrame.lookAt(jointPos, jointPos + (jointPos - torso.Position))), 0.8, 8)
		S.Blood.Wound(root, CFrame.new(0, root.Size.Y / 2, 0) * CFrame.Angles(math.rad(90), 0, 0), 0.6, 6)
		S.Blood.Pool(jointPos, 2.2, 2)
	end
	sev.Parent = workspace.Evidence
	Bodies.SetupPart(sev)
	return sev
end

-- ── Dragging ─────────────────────────────────────────────────────────────────
function Bodies.StartDrag(player: Player, body: Model)
	local st = S.GameState.Get(player)
	local char = player.Character
	if not st or not char or st.Busy or st.Carrying or st.Dragging then
		return
	end
	if body:GetAttribute("DraggedBy") then
		return
	end
	local torso = body:FindFirstChild("UpperTorso") :: BasePart?
	local hrp = char:FindFirstChild("HumanoidRootPart") :: BasePart?
	local hum = char:FindFirstChildOfClass("Humanoid")
	if not (torso and hrp and hum) then
		return
	end
	if S.Items.Equipped(player) then
		hum:UnequipTools()
	end
	st.Dragging = body
	body:SetAttribute("DraggedBy", player.UserId)
	local a0 = Instance.new("Attachment")
	a0.Name = "DragA0"
	a0.Position = Vector3.new(0, torso.Size.Y / 2, 0)
	a0.Parent = torso
	local a1 = Instance.new("Attachment")
	a1.Name = "DragA1"
	a1.Position = Vector3.new(0.9, -0.8, 2.4)
	a1.Parent = hrp
	local ap = Instance.new("AlignPosition")
	ap.Name = "DragAlign"
	ap.Attachment0 = a0
	ap.Attachment1 = a1
	ap.MaxForce = 9000
	ap.MaxVelocity = 40
	ap.Responsiveness = 30
	ap.Parent = torso
	setOwner(body, player)
	S.GameState.RefreshMood(player)
	local p = torso:FindFirstChild("DragPrompt") :: ProximityPrompt?
	if p then
		p.ActionText = "Drop"
		p.KeyboardKeyCode = Enum.KeyCode.G
	end
	-- blood trail while the body is fresh
	task.spawn(function()
		local last = torso.Position
		while st.Dragging == body and torso.Parent do
			local fresh = workspace:GetServerTimeNow() - (body:GetAttribute("DeathTime") or 0) < 240
			if fresh and (torso.Position - last).Magnitude > 1.8 then
				S.Blood.Smear(last, torso.Position, 0.9 + math.random() * 0.6)
				if math.random() < 0.25 then
					S.Blood.Pool(torso.Position, 0.8 + math.random() * 0.6, 0.8)
				end
				last = torso.Position
			end
			task.wait(0.35)
		end
	end)
end

function Bodies.StopDrag(player: Player)
	local st = S.GameState.Get(player)
	if not st or not st.Dragging then
		return
	end
	local body = st.Dragging
	st.Dragging = nil
	body:SetAttribute("DraggedBy", nil)
	local torso = body:FindFirstChild("UpperTorso")
	if torso then
		for _, n in { "DragA0", "DragAlign" } do
			local x = torso:FindFirstChild(n)
			if x then
				x:Destroy()
			end
		end
		local p = torso:FindFirstChild("DragPrompt") :: ProximityPrompt?
		if p then
			p.ActionText = "Drag"
			p.KeyboardKeyCode = Enum.KeyCode.E
		end
	end
	local char = player.Character
	if char then
		local hrp = char:FindFirstChild("HumanoidRootPart")
		local a1 = hrp and hrp:FindFirstChild("DragA1")
		if a1 then
			a1:Destroy()
		end
		S.GameState.RefreshMood(player)
	end
	if body.Parent then
		setOwner(body, nil)
	end
end

-- ── Carrying (limbs and bags) ────────────────────────────────────────────────
function Bodies.StartCarry(player: Player, model: Model, kind: string)
	local st = S.GameState.Get(player)
	local char = player.Character
	if not st or not char or st.Busy or st.Dragging then
		return
	end
	if st.Carrying == model then
		Bodies.StopCarry(player)
		return
	end
	if st.Carrying then
		return
	end
	local ut = char:FindFirstChild("UpperTorso") :: BasePart?
	local hum = char:FindFirstChildOfClass("Humanoid")
	local root = model.PrimaryPart
	if not (ut and hum and root) then
		return
	end
	if S.Items.Equipped(player) then
		hum:UnequipTools()
	end
	st.Carrying = model
	st.CarryKind = kind
	for _, p in model:GetDescendants() do
		if p:IsA("BasePart") then
			p.Anchored = false
			p.CanCollide = false
			p.Massless = true
		end
	end
	model:PivotTo(ut.CFrame * CFrame.new(0, -0.2, -1.5))
	local w = Instance.new("WeldConstraint")
	w.Name = "CarryWeld"
	w.Part0 = ut
	w.Part1 = root
	w.Parent = root
	S.GameState.RefreshMood(player)
	for _, pp in root:GetChildren() do
		if pp:IsA("ProximityPrompt") then
			pp.Enabled = pp.Name == "CarryPrompt"
			if pp.Name == "CarryPrompt" then
				pp.ActionText = "Put down"
				pp.KeyboardKeyCode = Enum.KeyCode.G
			end
		end
	end
end

function Bodies.StopCarry(player: Player)
	local st = S.GameState.Get(player)
	if not st or not st.Carrying then
		return
	end
	local model = st.Carrying
	st.Carrying = nil
	st.CarryKind = nil
	local root = model.PrimaryPart
	if root then
		local w = root:FindFirstChild("CarryWeld")
		if w then
			w:Destroy()
		end
		for _, p in model:GetDescendants() do
			if p:IsA("BasePart") then
				p.Massless = false
				p.CanCollide = not p:GetAttribute("Bone") or p == root
			end
		end
		for _, pp in root:GetChildren() do
			if pp:IsA("ProximityPrompt") then
				pp.Enabled = true
				if pp.Name == "CarryPrompt" then
					pp.ActionText = model:GetAttribute("IsBag") and "Carry" or "Pick up"
					pp.KeyboardKeyCode = Enum.KeyCode.E
				end
			end
		end
	end
	if player.Character then
		S.GameState.RefreshMood(player)
	end
end

-- ── Bagging ──────────────────────────────────────────────────────────────────
function Bodies.Bag(player: Player, target: Model)
	local st = S.GameState.Get(player)
	if not st or st.Busy or st.Dragging or st.Carrying then
		return
	end
	if not S.Items.HasItem(player, "TrashBags") then
		S.Net.Notify(player, "You need trash bags. Maintenance has some.", "info")
		return
	end
	local root = target.PrimaryPart or target:FindFirstChild("UpperTorso") :: BasePart?
	if not root then
		return
	end
	local char = player.Character
	st.Busy = true
	S.Net.Anim(char, "Wash", "Action")
	task.wait(1.1)
	S.Net.StopAnim(char, "Action")
	local pos = root.Position
	local bag = Instance.new("Model")
	bag.Name = "TrashBag"
	local p = Instance.new("Part")
	p.Name = "Bag"
	p.Size = Vector3.new(2.2, 2.4, 2.2)
	p.Color = Color3.fromRGB(18, 18, 22)
	p.Material = Enum.Material.SmoothPlastic
	p.Reflectance = 0.12
	p.CFrame = CFrame.new(pos + Vector3.new(0, 1.2, 0))
	p.CollisionGroup = "Ragdoll"
	Util.mesh(p, Enum.MeshType.Sphere)
	p.Parent = bag
	local knot = Util.deco(Vector3.new(0.5, 0.6, 0.5), p.CFrame * CFrame.new(0, 1.3, 0), Color3.fromRGB(18, 18, 22), bag, "Knot")
	Util.mesh(knot, Enum.MeshType.Sphere)
	knot.Massless = false
	Util.weld(p, knot)
	bag.PrimaryPart = p
	bag:SetAttribute("IsBag", true)
	bag:SetAttribute("Victim", victimName(target))
	CollectionService:AddTag(bag, "Bag")
	bag.Parent = workspace.Evidence
	S.GameState.MarkGone(victimName(target), target)
	target:Destroy()
	local pr = prompt(p, "CarryPrompt", "Carry", "Trash bag", Enum.KeyCode.E)
	pr.Triggered:Connect(function(pl)
		Bodies.StartCarry(pl, bag, "Bag")
	end)
	st.Busy = false
	S.Net.Notify(player, "Bagged. Nobody looks twice at a trash bag.", "info")
end

-- ── Disposal ─────────────────────────────────────────────────────────────────
local function vanish(model: Model, time: number)
	for _, p in model:GetDescendants() do
		if p:IsA("BasePart") then
			p.Anchored = true
			p.CanCollide = false
			Util.tween(p, time, { Transparency = 1 })
		elseif p:IsA("ProximityPrompt") or p:IsA("ParticleEmitter") then
			p:Destroy()
		end
	end
	task.delay(time, function()
		model:Destroy()
	end)
end

-- what = "Incinerator" | "Dumpster" | "Bury"
function Bodies.Dispose(player: Player, what: string): boolean
	local st = S.GameState.Get(player)
	if not st or st.Busy then
		return false
	end
	local char = player.Character
	local target: Model? = st.Dragging or st.Carrying
	if not target then
		return false
	end
	local kind = st.Dragging and "Body" or st.CarryKind
	if what == "Dumpster" and kind ~= "Bag" then
		S.Net.Notify(player, "Too obvious. Bag it first.", "warn")
		return true
	end
	if what == "Bury" and not S.Items.HasItem(player, "Shovel") then
		S.Net.Notify(player, "You need a shovel. Check the greenhouse.", "info")
		return true
	end
	if st.Dragging then
		Bodies.StopDrag(player)
	else
		Bodies.StopCarry(player)
	end
	st.Busy = true
	local name = victimName(target)
	if what == "Bury" then
		S.Net.Anim(char, "Dig", "Action")
		task.wait(3.3)
		S.Net.StopAnim(char, "Action")
		local plot = S.World.Interact.DigPlot
		local mound = Instance.new("Part")
		mound.Name = "Mound"
		mound.Anchored = true
		mound.CanCollide = false
		mound.Size = Vector3.new(3.5, 0.7, 2.5)
		mound.Color = Color3.fromRGB(80, 55, 38)
		mound.Material = Enum.Material.Ground
		mound.CFrame = CFrame.new(plot.Position.X + math.random(-3, 3), plot.Position.Y + 0.2, plot.Position.Z + math.random(-3, 3))
		Util.mesh(mound, Enum.MeshType.Sphere)
		mound.Parent = workspace.Evidence
		vanish(target, 0.6)
		S.Net.Sfx("BodyDrop", S.World.Interact.DigPlot, 0.7, 0.8)
		S.Net.Notify(player, "Buried. The roses will love it.", "info")
	elseif what == "Incinerator" then
		S.Net.Anim(char, "Carry", "Action")
		task.wait(0.8)
		S.Net.StopAnim(char, "Action")
		local mouth = S.World.Interact.Incinerator
		S.Net.Sfx("Burn", mouth, 0.9)
		target:PivotTo(CFrame.new(mouth.Position + Vector3.new(0, 0, -2)))
		vanish(target, 1.2)
		local smoke = mouth.Parent and mouth.Parent:FindFirstChild("Chimney")
		local pe = smoke and smoke:FindFirstChild("Smoke") :: ParticleEmitter?
		if pe then
			pe.Rate = 30
			task.delay(10, function()
				pe.Rate = 4
			end)
		end
		S.Net.Notify(player, "Burned. Gone.", "info")
	else
		target:PivotTo(S.World.Interact.Dumpster.CFrame * CFrame.new(0, 1, 0))
		vanish(target, 0.4)
		S.Net.Notify(player, "Trash day is tomorrow.", "info")
	end
	S.GameState.MarkGone(name, target)
	st.Busy = false
	return true
end

-- ── Utility tools ────────────────────────────────────────────────────────────
function Bodies.HookUtility(tool: Tool)
	local id = tool:GetAttribute("ItemId")
	if id == "Mop" then
		tool.Activated:Connect(function()
			local char = tool.Parent
			local player = char and Players:GetPlayerFromCharacter(char)
			if player then
				Bodies.Mop(player)
			end
		end)
	end
end

function Bodies.Mop(player: Player)
	local st = S.GameState.Get(player)
	local char = player.Character
	if not st or not char or st.Busy then
		return
	end
	local hrp = char:FindFirstChild("HumanoidRootPart") :: BasePart?
	if not hrp then
		return
	end
	st.Busy = true
	st.Mopping = true
	S.Net.Anim(char, "Mop", "Action")
	local cleanedAny = false
	for _ = 1, 3 do
		task.wait(0.55)
		local ahead = hrp.Position + hrp.CFrame.LookVector * 2.5 - Vector3.new(0, 3, 0)
		if S.Blood.CleanNear(ahead, 3.2, 1.6) then
			cleanedAny = true
		end
	end
	S.Net.StopAnim(char, "Action")
	st.Mopping = false
	st.Busy = false
	if not cleanedAny then
		S.Net.Notify(player, "Nothing to mop here.", "info")
	end
end

return Bodies

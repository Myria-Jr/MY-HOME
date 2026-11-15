-- KUROBARA · NPCs: daily schedules, pathfinding, perception and reactions

local CollectionService = game:GetService("CollectionService")
local PathfindingService = game:GetService("PathfindingService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ServerStorage = game:GetService("ServerStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Util = require(Shared.Util)

local NPC = {}
NPC.List = {}
NPC.Paused = true
NPC.Chasers = 0
local S
local awayFolder: Folder

local AGENT = {
	AgentRadius = 1.4,
	AgentHeight = 5,
	AgentCanJump = false,
	AgentCanClimb = false,
	WaypointSpacing = 6,
}

local NYX_SEAT = 6 -- seat index in 2-A reserved for the player

function NPC.Init(services)
	S = services
	awayFolder = Instance.new("Folder")
	awayFolder.Name = "AwayNPCs"
	awayFolder.Parent = ServerStorage
end

-- ── Helpers ──────────────────────────────────────────────────────────────────
local function hash(s: string): number
	local h = 0
	for i = 1, #s do
		h = (h * 31 + string.byte(s, i)) % 100000
	end
	return h
end

local function spotIn(group: string, rec): CFrame?
	local list = S.World.Spots[group]
	if not list or #list == 0 then
		return nil
	end
	return list[(hash(rec.def.id) % #list) + 1]
end

local function present(rec): boolean
	return rec.alive and rec.model ~= nil and rec.model.Parent == workspace.NPCs
end

function NPC.Present(rec)
	return present(rec)
end

local function setTalk(rec, on: boolean)
	local p = rec.root and rec.root:FindFirstChild("TalkPrompt") :: ProximityPrompt?
	if p then
		p.Enabled = on
	end
end

-- ── Spawning ─────────────────────────────────────────────────────────────────
local function newRec(def, role: string)
	return {
		def = def,
		role = role,
		alive = true,
		state = "Away",
		token = 0,
		targetKey = nil,
		arrival = 0,
		witness = false,
		filmed = false,
		sawBody = false,
		seenPools = {},
		lastNotice = {},
		patrolIndex = 1,
		alertUntil = 0,
		isHaru = def.id == "Haru",
		isRival = def.id == "Mirei",
	}
end

local function build(rec)
	local model = S.Rig.CreateNPC(rec.def, rec.role)
	rec.model = model
	rec.hum = model:FindFirstChildOfClass("Humanoid")
	rec.root = model:FindFirstChild("HumanoidRootPart")
	model.Parent = awayFolder
	S.Rig.AnimateNPC(model)
	if rec.isRival then
		S.Rig.SetExpression(model, "Smile")
	end
	local p = Instance.new("ProximityPrompt")
	p.Name = "TalkPrompt"
	p.ActionText = "Talk"
	p.ObjectText = rec.def.name
	p.KeyboardKeyCode = Enum.KeyCode.E
	p.MaxActivationDistance = 7
	p.RequiresLineOfSight = false
	p.Style = Enum.ProximityPromptStyle.Custom
	p.Enabled = false
	p.Parent = rec.root
	p.Triggered:Connect(function(player)
		S.Interact.Talk(player, rec)
	end)
end

function NPC.SpawnAll()
	NPC.List = {}
	local seatCounter = {}
	local function seatFor(class: string): number
		seatCounter[class] = (seatCounter[class] or 0) + 1
		if class == "2A" and seatCounter[class] == NYX_SEAT then
			seatCounter[class] += 1
		end
		return seatCounter[class]
	end
	local all = {}
	for _, d in Story.Students do
		table.insert(all, newRec(d, "Student"))
	end
	for i, d in Story.Extras or {} do
		if i > (Config.NPC.ExtraStudents or 0) then
			break
		end
		table.insert(all, newRec(d, "Student"))
	end
	table.insert(all, newRec(Story.Haru, "Student"))
	table.insert(all, newRec(Story.Mirei, "Student"))
	for _, d in Story.Teachers do
		table.insert(all, newRec(d, "Teacher"))
	end
	for _, rec in all do
		if rec.role == "Student" then
			rec.seat = seatFor(rec.def.class)
		end
		local ok, err = pcall(build, rec)
		if ok then
			table.insert(NPC.List, rec)
			task.spawn(NPC.Brain, rec)
		else
			warn("[Kurobara] failed to build NPC", rec.def.name, err)
		end
	end
	NPC.NewDay()
end

function NPC.ResetAll()
	for _, rec in NPC.List do
		rec.removed = true
		rec.token += 1
		if rec.model then
			rec.model:Destroy()
		end
	end
	NPC.List = {}
	for _, m in workspace.Evidence:GetChildren() do
		if m:IsA("Model") then
			m:Destroy()
		end
	end
	awayFolder:ClearAllChildren()
	workspace.NPCs:ClearAllChildren()
	NPC.SpawnAll()
end

function NPC.Get(id: string)
	for _, rec in NPC.List do
		if rec.def.id == id then
			return rec
		end
	end
	return nil
end

function NPC.PlayerSeat()
	local seats = S.World.Seats["2A"]
	return seats and seats[NYX_SEAT]
end

-- ── Day lifecycle ────────────────────────────────────────────────────────────
function NPC.NewDay()
	for _, rec in NPC.List do
		if rec.alive then
			NPC.Leave(rec)
			rec.sawBody = false
			rec.seenPools = {}
			rec.lastNotice = {}
			rec.witness = false
			rec.filmed = false
			rec.reported = false
			rec.reportedSusp = false
			rec.patrolIndex = 1
			if rec.role == "Teacher" then
				rec.arrival = 7 * 60 + math.random(0, 10)
			elseif rec.isRival then
				rec.arrival = 7 * 60 + 2
			elseif rec.isHaru then
				rec.arrival = 7 * 60 + 18
			else
				rec.arrival = 7 * 60 + math.random(4, 55)
			end
		end
	end
end

function NPC.Appear(rec)
	if not rec.alive or not rec.model then
		return
	end
	local exits = S.World.Spots.Exit
	local cf = exits[math.random(1, #exits)]
	rec.model:PivotTo(cf + Vector3.new(math.random(-3, 3), 3.2, math.random(-2, 2)))
	rec.model.Parent = workspace.NPCs
	rec.root.Anchored = false
	pcall(function()
		rec.root:SetNetworkOwner(nil)
	end)
	rec.state = "Routine"
	rec.targetKey = nil
	rec.nextWander = os.clock() + math.random(15, 70)
	rec.wanderUntil = nil
	rec.followUntil = nil
	rec.inDialogue = false
	S.Net.StopAnim(rec.model, "*")
	S.Rig.SetExpression(rec.model, rec.isRival and "Smile" or "Neutral")
	setTalk(rec, true)
end

function NPC.Leave(rec)
	rec.token += 1
	NPC.Unsit(rec)
	rec.state = "Away"
	rec.targetKey = nil
	setTalk(rec, false)
	if rec.model and rec.alive then
		rec.model.Parent = awayFolder
	end
end

-- ── Movement ─────────────────────────────────────────────────────────────────
local function waitMove(hum: Humanoid, timeout: number): boolean
	local done, result = false, false
	local conn = hum.MoveToFinished:Connect(function(reached)
		done = true
		result = reached
	end)
	local t = 0
	while not done and t < timeout do
		t += task.wait(0.1)
	end
	conn:Disconnect()
	return result
end

function NPC.Walk(rec, dest: Vector3, speed: number, token: number): boolean
	local hum, root = rec.hum, rec.root
	if not (hum and root) then
		return false
	end
	hum.WalkSpeed = speed
	for _attempt = 1, 2 do
		local path = PathfindingService:CreatePath(AGENT)
		local ok = pcall(function()
			path:ComputeAsync(root.Position, dest)
		end)
		local points: { Vector3 } = {}
		if ok and path.Status == Enum.PathStatus.Success then
			for i, wp in path:GetWaypoints() do
				if i > 1 then
					table.insert(points, wp.Position)
				end
			end
		else
			points = { dest }
		end
		local stuck = false
		for _, pos in points do
			if rec.token ~= token or not rec.alive or NPC.Paused then
				return false
			end
			hum:MoveTo(pos)
			if not waitMove(hum, 4) then
				stuck = true
				break
			end
		end
		if not stuck then
			return true
		end
		if rec.token ~= token then
			return false
		end
	end
	return (Util.flat(root.Position) - Util.flat(dest)).Magnitude < 4
end

function NPC.Sit(rec, seat)
	rec.root.Anchored = true
	rec.root.CFrame = seat.sit
	rec.model:SetAttribute("Sitting", true)
	rec.sitting = seat
end

function NPC.Unsit(rec)
	if rec.sitting and rec.root then
		local seat = rec.sitting
		rec.sitting = nil
		rec.model:SetAttribute("Sitting", false)
		rec.root.CFrame = CFrame.new(seat.approach + Vector3.new(0, 3.2, 0)) * seat.sit.Rotation
		rec.root.Anchored = false
	end
end

local function faceDir(rec, look: Vector3)
	local root = rec.root
	look = Util.flat(look)
	if look.Magnitude < 0.01 then
		return
	end
	root.CFrame = CFrame.lookAt(root.Position, root.Position + look.Unit)
end

-- ── Schedule ─────────────────────────────────────────────────────────────────
-- returns key, target { cf=CFrame?, seat=?, exit=bool, patrol=bool }
function NPC.Target(rec)
	local period = S.Clock.Period().id
	local minute = S.Clock.Minutes
	local day = S.GameState.Day
	local def = rec.def
	local function at(group: string, idx: number?)
		local list = S.World.Spots[group]
		if not list or #list == 0 then
			return nil
		end
		local cf = idx and list[math.clamp(idx, 1, #list)] or spotIn(group, rec)
		return group .. (idx or ""), { cf = cf }
	end
	if period == "Leaving" and (rec.role ~= "Teacher" or minute >= 17 * 60 + 50) then
		return "Exit", { cf = S.World.Spots.Exit[1], exit = true }
	end
	if rec.role == "Teacher" then
		if def.patrol then
			if period == "Morning" then
				return at("Gate", 3)
			end
			local hall = S.World.Spots.Hall
			local key, t = "Patrol" .. rec.patrolIndex, { cf = hall[((rec.patrolIndex - 1) % #hall) + 1], patrol = true }
			return key, t
		end
		if (period == "Class1" or period == "Class2") and S.World.ClassFront[def.class] then
			return "Front", { cf = S.World.ClassFront[def.class] }
		end
		return at("Lounge")
	end
	local classTime = period == "Class1" or period == "Class2"
	if rec.inDialogue then
		return "Dialogue", { stay = true }
	end
	if rec.followUntil then
		if classTime then
			rec.followUntil = nil
			S.Rig.Say(rec.model, Util.pick(Story.Dialogue.FollowClass), nil, 2.5)
		elseif os.clock() < rec.followUntil then
			return "FollowNyx", { followPlayer = true }
		else
			rec.followUntil = nil
		end
	end
	if rec.wanderUntil and not classTime and period ~= "Leaving" then
		if os.clock() < rec.wanderUntil and rec.wanderCF then
			return "Wander" .. math.floor(rec.wanderUntil), { cf = rec.wanderCF }
		end
		rec.wanderUntil = nil
	end
	-- Ryo tails Nyx between classes once he's suspicious
	if def.persona == "Detective" and S.GameState.Investigating and period ~= "Class1" and period ~= "Class2" then
		return "Follow", { follow = true }
	end
	-- the confession
	if day == Config.ConfessionDay and minute >= Config.ConfessionTime - 15 and (rec.isHaru or rec.isRival) then
		return at("RoofConfess", rec.isHaru and 1 or 2)
	end
	if period == "Class1" or period == "Class2" then
		local seats = S.World.Seats[def.class]
		local seat = seats and seats[rec.seat or 1]
		if seat then
			return "Seat", { seat = seat }
		end
	end
	if rec.isRival then
		if period == "Morning" then
			if minute < 7 * 60 + 45 then
				return at("Greenhouse", 1)
			end
			return at("Courtyard", 2)
		elseif period == "AfterSchool" then
			if minute >= 17 * 60 then
				return at("Greenhouse", 1)
			end
			return at("Garden", 1)
		end
	end
	if rec.isHaru then
		if period == "Morning" then
			return at("FountainBench", 1)
		elseif period == "Lunch" then
			return at("RoofHaru", 1)
		end
	end
	if period == "Morning" then
		return at(def.morning or "Courtyard")
	elseif period == "Lunch" then
		return at(def.lunch or "Cafeteria")
	elseif period == "AfterSchool" then
		return at(def.club or "Courtyard")
	end
	return at("Courtyard")
end

function NPC.GoTo(rec, target)
	rec.token += 1
	local token = rec.token
	NPC.Unsit(rec)
	rec.atSpot = false
	if target.follow then
		task.spawn(NPC.Follow, rec, token)
		return
	elseif target.followPlayer then
		task.spawn(NPC.FollowNyx, rec, token)
		return
	elseif target.stay then
		if rec.hum and rec.root and not rec.sitting then
			rec.hum:MoveTo(rec.root.Position)
		end
		rec.atSpot = true
		return
	end
	task.spawn(function()
		local dest
		if target.seat then
			dest = target.seat.approach
		elseif target.cf then
			dest = target.cf.Position
			if not target.exit and not target.patrol then
				dest += Vector3.new((hash(rec.def.id) % 7 - 3) * 0.35, 0, (hash(rec.def.id .. "z") % 7 - 3) * 0.35)
			end
		else
			return
		end
		local ok = NPC.Walk(rec, dest, Config.NPC.WalkSpeed, token)
		if rec.token ~= token or not rec.alive or rec.state ~= "Routine" then
			return
		end
		if target.seat and ok then
			NPC.Sit(rec, target.seat)
		elseif target.cf then
			faceDir(rec, target.cf.LookVector)
		end
		rec.atSpot = true
		rec.nextIdle = os.clock() + 2 + math.random() * 6
		if target.exit then
			NPC.Leave(rec)
		elseif target.patrol then
			task.wait(2.5)
			if rec.token == token then
				rec.patrolIndex += 1
			end
		end
	end)
end

-- ── Brain ────────────────────────────────────────────────────────────────────
function NPC.Brain(rec)
	local chatter = os.clock() + math.random(8, 30)
	while not rec.removed and rec.alive do
		if NPC.Paused or rec.state == "Victim" then
			task.wait(0.3)
			continue
		end
		if rec.state == "Away" then
			local m = S.Clock.Minutes
			if S.GameState.Phase == "Playing" and m >= rec.arrival and m < 17 * 60 + 15 then
				NPC.Appear(rec)
			end
		elseif rec.state == "Routine" and present(rec) then
			local key, target = NPC.Target(rec)
			if key and key ~= rec.targetKey then
				rec.targetKey = key
				NPC.GoTo(rec, target)
			end
			NPC.Perceive(rec)
			if rec.role == "Student" and rec.state == "Routine" and rec.atSpot and not rec.talking and not rec.inDialogue
				and not rec.followUntil and not rec.wanderUntil and not rec.isHaru and not rec.isRival
				and os.clock() > (rec.nextWander or math.huge) then
				NPC.StartWander(rec)
			elseif rec.state == "Routine" and rec.atSpot and not rec.talking and not rec.inDialogue and os.clock() > (rec.nextIdle or 0) then
				NPC.IdleAction(rec)
			elseif os.clock() > chatter and rec.role == "Student" and rec.atSpot and not rec.talking then
				chatter = os.clock() + math.random(18, 45)
				S.Net.Anim(rec.model, "Talk", "Action")
			end
		elseif rec.state == "Shaken" and present(rec) then
			-- cowering until the day ends
		end
		task.wait(Config.Vision.TickRate + math.random() * 0.15)
	end
end

-- Short walks to somewhere else on campus, then back to their usual spot
local WANDER = {
	Morning = { "Courtyard", "Fountain", "Lobby", "Gate", "Hall", "Field" },
	Lunch = { "Cafeteria", "Courtyard", "Roof", "Hall", "Fountain", "Lobby" },
	AfterSchool = { "Hall", "Courtyard", "Field", "Gym", "Garden", "Fountain", "Library" },
}

function NPC.StartWander(rec)
	local w = Config.NPC.WanderEvery
	rec.nextWander = os.clock() + math.random(w[1], w[2])
	local groups = WANDER[S.Clock.Period().id]
	if not groups then
		return
	end
	local list = S.World.Spots[Util.pick(groups)]
	if not list or #list == 0 then
		return
	end
	rec.wanderCF = list[math.random(1, #list)]
	rec.wanderUntil = os.clock() + math.random(30, 55)
end

-- "Can you come with me?" — they tag along until told to wait, class starts or 2 minutes pass
function NPC.StartFollowing(rec)
	rec.followUntil = os.clock() + 120
	rec.wanderUntil = nil
	rec.targetKey = nil
end

function NPC.StopFollowing(rec)
	rec.followUntil = nil
	rec.targetKey = nil
end

-- Little things people do while waiting around
local IDLE = {
	Default = { "LookAround", "PhoneIdle", "Stretch", "Talk", "Yawn", "Laugh", "HandsBehind", "Wave" },
	Seated = { "Write", "Write", "LookAround", "Yawn", "Talk" },
	Teacher = { "LookAround", "ArmsCrossed", "HandsBehind", "Talk" },
	Delinquent = { "ArmsCrossed", "Stretch", "LookAround", "Laugh" },
	Gossip = { "Talk", "Laugh", "PhoneIdle", "Wave", "Talk" },
	Detective = { "PhoneIdle", "LookAround", "ArmsCrossed" },
}
local HOLD = { ArmsCrossed = true, HandsBehind = true, PhoneIdle = true }

function NPC.IdleAction(rec)
	rec.nextIdle = os.clock() + 6 + math.random() * 10
	local pool
	if rec.sitting then
		pool = IDLE.Seated
	elseif rec.role == "Teacher" then
		pool = IDLE.Teacher
	else
		pool = IDLE[rec.def.persona] or IDLE.Default
	end
	local clip = Util.pick(pool)
	S.Net.Anim(rec.model, clip, "Action")
	if HOLD[clip] then
		local token = rec.token
		task.delay(3 + math.random() * 3, function()
			if rec.token == token and rec.model and rec.model.Parent then
				S.Net.Anim(rec.model, false, "Action")
			end
		end)
	end
end

function NPC.SetPaused(paused: boolean)
	NPC.Paused = paused
	for _, rec in NPC.List do
		if present(rec) and rec.hum and not rec.sitting then
			rec.hum:MoveTo(rec.root.Position)
		end
	end
end

-- ── Perception ───────────────────────────────────────────────────────────────
local visionParams = RaycastParams.new()
visionParams.FilterType = Enum.RaycastFilterType.Exclude

local function canSee(rec, targetPos: Vector3, targetModel: Instance?, wide: boolean?): boolean
	local head = rec.model:FindFirstChild("Head") :: BasePart?
	if not head then
		return false
	end
	local to = targetPos - head.Position
	local dist = to.Magnitude
	if dist > Config.Vision.Range or dist < 0.01 then
		return false
	end
	local fov = (wide or os.clock() < rec.alertUntil) and 300 or Config.Vision.FOV
	local look = rec.sitting and rec.sitting.sit.LookVector or rec.root.CFrame.LookVector
	if look:Dot(to.Unit) < math.cos(math.rad(fov / 2)) then
		return false
	end
	local ignore = { rec.model, workspace.NPCs, workspace.Items, workspace.Evidence:FindFirstChild("Blood") }
	if targetModel then
		table.insert(ignore, targetModel)
	end
	visionParams.FilterDescendantsInstances = ignore
	local hit = workspace:Raycast(head.Position, to, visionParams)
	return hit == nil
end

local function cooldown(rec, key: string, seconds: number): boolean
	local now = os.clock()
	if now - (rec.lastNotice[key] or -1e9) < seconds then
		return false
	end
	rec.lastNotice[key] = now
	return true
end

function NPC.Perceive(rec)
	local player, st = S.GameState.MainPlayer()
	if not player or not st then
		return
	end
	local char = player.Character
	local ut = char and char:FindFirstChild("UpperTorso") :: BasePart?
	if not ut then
		return
	end
	local seesPlayer = canSee(rec, ut.Position, char)
	local dist = (ut.Position - rec.root.Position).Magnitude

	-- 1) murder-level: attacking, dragging, carrying a limb, chopping
	if seesPlayer and (st.Attacking or st.Dragging or st.Dismembering or (st.Carrying and st.CarryKind ~= "Bag")) then
		NPC.ReactPlayer(rec, 3, "Murder", dist)
		return
	end
	-- 2) corpses & body parts
	for _, tag in { "Body", "BodyPart" } do
		for _, body in CollectionService:GetTagged(tag) do
			if body.Parent and body:IsDescendantOf(workspace) and not body:GetAttribute("DraggedBy") then
				local part = (body.PrimaryPart or body:FindFirstChild("UpperTorso") or body:FindFirstChildWhichIsA("BasePart")) :: BasePart?
				if part and (part.Position - rec.root.Position).Magnitude < Config.Vision.Range and canSee(rec, part.Position, body) then
					NPC.ReactCorpse(rec, part.Position)
					return
				end
			end
		end
	end
	-- 3) player looks wrong
	if seesPlayer then
		local tool = char:FindFirstChildOfClass("Tool")
		local bloodyTool = tool and tool:GetAttribute("Bloody")
		if st.Bloodiness >= 20 or bloodyTool then
			NPC.ReactPlayer(rec, 2, "Bloody", dist)
			return
		end
		local sus = tool and (tool:GetAttribute("Suspicion") or 0) or 0
		if sus > 0.2 and dist < Config.Vision.WeaponRange * sus then
			NPC.ReactPlayer(rec, 1, "Weapon", dist)
			return
		end
		if st.BPM >= Config.BPM.Unstable and dist < Config.Vision.CreepyRange then
			NPC.ReactPlayer(rec, 1, "Creepy", dist)
			return
		end
		if st.Mopping and dist < 25 then
			NPC.ReactPlayer(rec, 1, "Mopping", dist)
		end
	end
	-- 4) blood on the floor
	for _, pool in CollectionService:GetTagged("BloodPool") do
		if pool.Parent and not rec.seenPools[pool] and (pool:GetAttribute("Size") or 0) >= Config.Vision.PoolMinSize then
			if (pool.Position - rec.root.Position).Magnitude < 32 and canSee(rec, pool.Position + Vector3.new(0, 0.2, 0), pool) then
				rec.seenPools[pool] = true
				NPC.ReactBlood(rec, pool)
				return
			end
		end
	end
end

-- ── Reactions ────────────────────────────────────────────────────────────────
function NPC.ReactPlayer(rec, severity: number, kind: string, _dist: number)
	local player = S.GameState.MainPlayer()
	if not player then
		return
	end
	if not cooldown(rec, kind, severity >= 2 and 6 or 20) then
		return
	end
	local model = rec.model
	if rec.isHaru and severity >= 2 then
		S.Rig.SetExpression(model, "Scared")
		S.Rig.Say(model, "N-Nyx…? What did you do…", Config.UI.Warn, 4)
		S.GameState.GameOver(player, "Heartbreak")
		return
	end
	S.GameState.Seen(player, rec, kind)
	if severity == 3 then
		rec.witness = true
		S.GameState.AddRep(player, -30)
		S.Rig.Alert(model, "!!")
		S.Rig.SetExpression(model, "Scared")
		if rec.role == "Teacher" then
			NPC.Chase(rec, "Teacher")
		elseif rec.def.persona == "Hero" or rec.def.persona == "Delinquent" then
			NPC.Chase(rec, rec.def.persona)
		elseif rec.def.persona == "Phone" or rec.def.persona == "Detective" then
			if rec.def.persona == "Detective" then
				S.GameState.SetSuspicion(100)
			end
			NPC.Film(rec)
		elseif rec.def.persona == "Gossip" then
			NPC.Gossip(rec)
			NPC.Scream(rec.root.Position, rec)
			NPC.Flee(rec, "Murder")
		else
			S.Rig.Say(model, Util.pick(Story.Lines.Scream), Config.UI.Warn, 2.5)
			NPC.Scream(rec.root.Position, rec)
			NPC.Flee(rec, "Murder")
		end
	elseif severity == 2 then
		S.GameState.AddRep(player, -12)
		S.Rig.Alert(model, "!")
		if rec.role == "Teacher" then
			NPC.Chase(rec, "Teacher")
		elseif rec.def.persona == "Delinquent" then
			NPC.Chase(rec, "Delinquent")
		elseif rec.def.persona == "Detective" then
			S.Rig.Say(model, "That's blood. I'm writing this down.", Config.UI.Warn, 3)
			S.GameState.StartInvestigation()
			S.GameState.AddSuspicion(Config.Detective.Major)
		else
			S.Rig.SetExpression(model, "Scared")
			S.Rig.Say(model, Util.pick(Story.Lines.Bloody), Config.UI.Warn, 3)
			if rec.def.persona == "Gossip" then
				S.GameState.AddRep(player, -10)
				NPC.Gossip(rec)
			end
			NPC.Flee(rec, "Report")
		end
	else
		S.GameState.AddRep(player, rec.role == "Teacher" and -5 or -3)
		S.Rig.Alert(model, "?", Color3.fromRGB(255, 214, 10))
		local lines = kind == "Weapon" and Story.Lines.Weapon or Story.Lines.Creepy
		if rec.role == "Teacher" then
			lines = kind == "Weapon" and { "Put that away, Amane. Now." } or Story.Lines.Teacher
		elseif kind == "Mopping" then
			lines = { "Why are you mopping…?", "Did someone spill something?" }
		end
		if rec.def.persona == "Detective" then
			lines = Story.Lines.Detective
			S.GameState.AddSuspicion(Config.Detective.Minor)
		elseif rec.def.persona == "Gossip" then
			S.GameState.AddRep(player, -3)
			NPC.Gossip(rec)
		end
		if rec.def.persona ~= "Gossip" then
			S.Rig.Say(model, Util.pick(lines), nil, 3)
		end
		local char = player.Character
		local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
		if hrp and not rec.sitting then
			faceDir(rec, hrp.Position - rec.root.Position)
		end
	end
end

function NPC.ReactCorpse(rec, pos: Vector3)
	if rec.sawBody then
		return
	end
	rec.sawBody = true
	local model = rec.model
	S.Rig.Alert(model, "!!")
	S.Rig.SetExpression(model, "Scared")
	S.Rig.Say(model, Util.pick(Story.Lines.Corpse), Config.UI.Warn, 3)
	S.Net.Anim(model, "Scream", "Action")
	NPC.Scream(rec.root.Position, rec)
	local player = S.GameState.MainPlayer()
	if player then
		S.Net.Notify(player, rec.def.name .. " found a body!", "warn")
	end
	S.GameState.StartInvestigation()
	if rec.role == "Teacher" then
		S.GameState.CallPolice(rec.def.name .. " found a body.")
		rec.token += 1
		local token = rec.token
		NPC.Unsit(rec)
		rec.state = "Guard"
		setTalk(rec, false)
		task.spawn(function()
			NPC.Walk(rec, pos + (rec.root.Position - pos).Unit * 4, Config.NPC.ChaseSpeed, token)
		end)
	else
		NPC.Flee(rec, "Report")
	end
end

function NPC.ReactBlood(rec, pool: BasePart)
	local model = rec.model
	S.Rig.Alert(model, "?", Color3.fromRGB(255, 214, 10))
	S.Rig.Say(model, Util.pick(Story.Lines.Blood), nil, 3)
	if rec.role == "Teacher" then
		S.GameState.CallPolice(rec.def.name .. " found blood in the school.")
	elseif math.random() < 0.6 then
		NPC.Flee(rec, "Report")
	end
end

-- Everyone close enough turns toward the noise
function NPC.Scream(pos: Vector3, source)
	for _, rec in NPC.List do
		if rec ~= source and present(rec) and rec.state == "Routine" then
			if (rec.root.Position - pos).Magnitude < Config.NPC.ScreamRadius then
				rec.alertUntil = os.clock() + 4
				if not rec.sitting then
					faceDir(rec, pos - rec.root.Position)
				end
			end
		end
	end
end

local function nearestTeacher(from: Vector3)
	local best, bestD = nil, math.huge
	for _, rec in NPC.List do
		if rec.role == "Teacher" and present(rec) and rec.state ~= "Chase" then
			local d = (rec.root.Position - from).Magnitude
			if d < bestD then
				best, bestD = rec, d
			end
		end
	end
	return best
end

function NPC.Flee(rec, reason: string)
	rec.token += 1
	local token = rec.token
	NPC.Unsit(rec)
	rec.state = "Flee"
	setTalk(rec, false)
	S.Net.Anim(rec.model, "Panic", "Base")
	task.spawn(function()
		local dest
		local teacher = reason == "Report" and nearestTeacher(rec.root.Position)
		if teacher then
			dest = teacher.root.Position
		else
			dest = S.World.Spots.Exit[1].Position
		end
		local ok = NPC.Walk(rec, dest, Config.NPC.FleeSpeed, token)
		if rec.token ~= token or not rec.alive then
			return
		end
		S.Net.StopAnim(rec.model, "Base")
		if ok or (rec.root.Position - dest).Magnitude < 10 then
			if reason == "Murder" then
				if rec.filmed then
					S.GameState.PhotoEvidence = true
				end
				S.GameState.CallPolice(rec.def.name .. " escaped and called the police.")
				NPC.Leave(rec)
			else
				rec.reported = true
				S.GameState.CallPolice(rec.def.name .. " reported you to a teacher.")
				rec.state = "Shaken"
				S.Net.Anim(rec.model, "Cower", "Base")
			end
		else
			rec.state = "Shaken"
		end
	end)
end

function NPC.Film(rec)
	rec.token += 1
	local token = rec.token
	NPC.Unsit(rec)
	rec.state = "Film"
	setTalk(rec, false)
	local player = S.GameState.MainPlayer()
	task.spawn(function()
		local char = player and player.Character
		local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
		if hrp then
			faceDir(rec, hrp.Position - rec.root.Position)
		end
		rec.hum:MoveTo(rec.root.Position)
		S.Net.Anim(rec.model, "Phone", "Action")
		S.Rig.Say(rec.model, Util.pick(Story.Lines.Phone), Config.UI.Warn, 2.5)
		task.delay(1.2, function()
			if rec.alive then
				S.Net.Sfx("Shutter", rec.root, 0.8)
			end
		end)
		if player then
			S.Net.Screen(player, "Flash", {})
		end
		task.wait(2.2)
		if rec.token ~= token or not rec.alive then
			return
		end
		rec.filmed = true
		S.Net.StopAnim(rec.model, "Action")
		NPC.Flee(rec, "Murder")
	end)
end

function NPC.Chase(rec, kind: string)
	rec.token += 1
	local token = rec.token
	NPC.Unsit(rec)
	rec.state = "Chase"
	setTalk(rec, false)
	local chaseLines = kind == "Teacher" and Story.Lines.TeacherChase or (kind == "Delinquent" and Story.Lines.Delinquent or Story.Lines.Hero)
	S.Rig.Say(rec.model, Util.pick(chaseLines), Config.UI.Warn, 2.5)
	local player, st = S.GameState.MainPlayer()
	task.spawn(function()
		NPC.Chasers += 1
		workspace:SetAttribute("Chase", true)
		local function done()
			NPC.Chasers = math.max(0, NPC.Chasers - 1)
			workspace:SetAttribute("Chase", NPC.Chasers > 0)
		end
		local started = os.clock()
		local lastSeen = os.clock()
		local speed = kind == "Teacher" and Config.NPC.ChaseSpeed or Config.NPC.HeroChaseSpeed
		if kind == "Delinquent" then
			speed += 0.6
		end
		while rec.token == token and rec.alive and S.GameState.Phase == "Playing" do
			local char = player and player.Character
			local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
			if not hrp then
				break
			end
			local d = (hrp.Position - rec.root.Position).Magnitude
			if d <= Config.NPC.CatchDistance and st and not st.Attacking then
				rec.hum:MoveTo(rec.root.Position)
				S.Net.Anim(rec.model, "Arrest", "Action")
				done()
				S.GameState.GameOver(player, kind == "Teacher" and "Apprehended" or "Overpowered")
				return
			end
			if canSee(rec, hrp.Position, char, true) then
				lastSeen = os.clock()
			end
			rec.hum.WalkSpeed = speed
			rec.hum:MoveTo(hrp.Position)
			if os.clock() - lastSeen > 6 or os.clock() - started > 40 then
				-- lost her
				if kind == "Teacher" then
					S.GameState.CallPolice(rec.def.name .. " lost sight of you and called the police.")
					rec.state = "Routine"
					rec.targetKey = nil
					setTalk(rec, true)
				else
					NPC.Flee(rec, "Murder")
				end
				done()
				return
			end
			task.wait(0.2)
		end
		done()
	end)
end

-- ── Conversations & lessons ──────────────────────────────────────────────────
local function conversationPool()
	local GS = S.GameState
	local rumor = not GS.RivalDown and GS.Day < Config.ConfessionDay
	local friday = not GS.RivalDown and GS.Day == Config.ConfessionDay
	local missing = #GS.Killed > 0
	local pool = {}
	for _, c in Story.Conversations do
		if c.when == "always" or (c.when == "rumor" and rumor) or (c.when == "friday" and friday) or (c.when == "missing" and missing) then
			table.insert(pool, c)
			if c.when ~= "always" then
				table.insert(pool, c) -- topical talk is twice as likely
			end
		end
	end
	return pool
end

function NPC.Converse(a, b, convo)
	a.talking, b.talking = true, true
	local ta, tb = a.token, b.token
	local missing = S.GameState.Killed[#S.GameState.Killed] or "someone"
	for _, line in convo.lines do
		if a.token ~= ta or b.token ~= tb or not (present(a) and present(b)) or a.state ~= "Routine" or b.state ~= "Routine" or NPC.Paused then
			break
		end
		local speaker = line[1] == "A" and a or b
		local listener = speaker == a and b or a
		local text = line[2]:gsub("{missing}", missing)
		if not speaker.sitting then
			faceDir(speaker, listener.root.Position - speaker.root.Position)
		end
		if not listener.sitting then
			faceDir(listener, speaker.root.Position - listener.root.Position)
		end
		S.Net.Anim(speaker.model, "Talk", "Action")
		S.Rig.Say(speaker.model, text, nil, 3.6)
		task.wait(2.2 + #text * 0.05)
	end
	a.talking, b.talking = false, false
	a.nextIdle = os.clock() + 5
	b.nextIdle = os.clock() + 5
end

-- People near Nyx chat with each other; teachers lecture to whoever is still in class.
function NPC.SocialLoop()
	task.spawn(function()
		while true do
			task.wait(6 + math.random() * 5)
			if NPC.Paused or S.GameState.Phase ~= "Playing" then
				continue
			end
			local player = S.GameState.MainPlayer()
			local char = player and player.Character
			local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
			if not hrp then
				continue
			end
			local near = {}
			for _, rec in NPC.List do
				if present(rec) and rec.state == "Routine" and rec.atSpot and not rec.talking then
					local d = (rec.root.Position - hrp.Position).Magnitude
					if d < 45 then
						table.insert(near, { rec = rec, d = d })
					end
					if rec.role == "Teacher" and rec.targetKey == "Front" and d < 45 and os.clock() > (rec.nextLecture or 0) then
						rec.nextLecture = os.clock() + 14 + math.random() * 8
						S.Net.Anim(rec.model, "Point", "Action")
						S.Rig.Say(rec.model, Util.pick(Story.Lecture), nil, 3.5)
						task.delay(1.5, function()
							if rec.model and rec.model.Parent then
								S.Net.Anim(rec.model, false, "Action")
							end
						end)
					end
				end
			end
			table.sort(near, function(x, y)
				return x.d < y.d
			end)
			local started = false
			for _, x in near do
				if started then
					break
				end
				if x.rec.role == "Student" and not x.rec.sitting then
					for _, y in near do
						if y.rec ~= x.rec and y.rec.role == "Student" and not y.rec.sitting and (y.rec.root.Position - x.rec.root.Position).Magnitude < 9 then
							local pool = conversationPool()
							if #pool > 0 then
								task.spawn(NPC.Converse, x.rec, y.rec, Util.pick(pool))
								started = true
							end
							break
						end
					end
				end
			end
		end
	end)
end

-- ── Personalities ────────────────────────────────────────────────────────────
-- Gossips make sure everyone nearby hears about it
function NPC.Gossip(rec)
	S.Rig.Say(rec.model, Util.pick(Story.Lines.Gossip), Config.UI.Love, 3)
	S.Net.Anim(rec.model, "Point", "Action")
	task.delay(1.2, function()
		if rec.model and rec.model.Parent then
			S.Net.Anim(rec.model, false, "Action")
		end
	end)
	for _, other in NPC.List do
		if other ~= rec and present(other) and other.state == "Routine" and (other.root.Position - rec.root.Position).Magnitude < 40 then
			other.alertUntil = os.clock() + 6
		end
	end
end

function NPC.SeesPlayer(rec): boolean
	local player = S.GameState.MainPlayer()
	local char = player and player.Character
	local ut = char and char:FindFirstChild("UpperTorso") :: BasePart?
	if not ut or not present(rec) then
		return false
	end
	return canSee(rec, ut.Position, char)
end

-- Ryo saw the attack coming: hop back, shout, run for it with everything he wrote down
function NPC.Dodge(rec)
	local player = S.GameState.MainPlayer()
	NPC.Unsit(rec)
	rec.token += 1
	rec.witness = true
	rec.filmed = true
	S.Rig.Say(rec.model, Util.pick(Story.Lines.Dodge), Config.UI.Warn, 2.5)
	S.Rig.Alert(rec.model, "!!")
	S.Rig.SetExpression(rec.model, "Scared")
	local char = player and player.Character
	local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
	if hrp then
		local away = Util.flat(rec.root.Position - hrp.Position)
		if away.Magnitude > 0.1 then
			rec.root.CFrame = CFrame.lookAt(rec.root.Position + away.Unit * 3, rec.root.Position + away.Unit * 6)
		end
	end
	S.GameState.SetSuspicion(100)
	if player then
		S.GameState.Seen(player, rec, "Murder")
		S.GameState.AddRep(player, -25)
	end
	NPC.Flee(rec, "Murder")
end

-- Suspicion maxed: he takes his notebook to a teacher
function NPC.DetectiveReport()
	for _, rec in NPC.List do
		if rec.def.persona == "Detective" and present(rec) and not rec.reportedSusp and rec.state == "Routine" then
			rec.reportedSusp = true
			rec.witness = true
			S.Rig.Say(rec.model, "That's enough. I'm going to the teachers.", Config.UI.Warn, 3)
			local player = S.GameState.MainPlayer()
			if player then
				S.Net.Notify(player, "🔍 Ryo is taking his notes to a teacher. Stop him!", "warn")
			end
			NPC.Flee(rec, "Report")
		end
	end
end

-- Walk a couple of steps behind Nyx
function NPC.FollowNyx(rec, token: number)
	local lastPath = 0
	while rec.token == token and rec.alive and rec.state == "Routine" do
		if NPC.Paused then
			task.wait(0.4)
			continue
		end
		local player = S.GameState.MainPlayer()
		local char = player and player.Character
		local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
		if not hrp then
			task.wait(0.4)
			continue
		end
		local to = Util.flat(hrp.Position - rec.root.Position)
		local d = to.Magnitude
		if d > 5 then
			rec.hum.WalkSpeed = d > 14 and 17 or 11
			local direct = canSee(rec, hrp.Position, char, true)
			if direct or d < 12 then
				rec.hum:MoveTo(hrp.Position - to.Unit * 3.5)
			elseif os.clock() - lastPath > 1 then
				lastPath = os.clock()
				local path = PathfindingService:CreatePath(AGENT)
				local ok = pcall(function()
					path:ComputeAsync(rec.root.Position, hrp.Position)
				end)
				if ok and path.Status == Enum.PathStatus.Success then
					local wps = path:GetWaypoints()
					local wp = wps[math.min(3, #wps)]
					if wp then
						rec.hum:MoveTo(wp.Position)
					end
				end
			end
		else
			rec.hum:MoveTo(rec.root.Position)
			if d > 0.1 then
				faceDir(rec, to)
			end
		end
		task.wait(0.25)
	end
end

-- Ryo keeps his distance, watches, writes. His suspicion only climbs while he can see you.
function NPC.Follow(rec, token: number)
	local lastLine = os.clock()
	local lastPath = 0
	while rec.token == token and rec.alive and rec.state == "Routine" do
		if NPC.Paused then
			task.wait(0.5)
			continue
		end
		local player = S.GameState.MainPlayer()
		local char = player and player.Character
		local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
		local ut = char and char:FindFirstChild("UpperTorso") :: BasePart?
		if not (hrp and ut) then
			task.wait(0.5)
			continue
		end
		local to = Util.flat(hrp.Position - rec.root.Position)
		local d = to.Magnitude
		local sees = canSee(rec, ut.Position, char, true)
		if d > Config.Detective.FollowDistance + 3 then
			rec.hum.WalkSpeed = d > 45 and 15 or Config.NPC.WalkSpeed + 2
			if sees then
				rec.hum:MoveTo(hrp.Position - to.Unit * Config.Detective.FollowDistance)
			elseif os.clock() - lastPath > 1.2 then
				lastPath = os.clock()
				local path = PathfindingService:CreatePath(AGENT)
				local ok = pcall(function()
					path:ComputeAsync(rec.root.Position, hrp.Position)
				end)
				if ok and path.Status == Enum.PathStatus.Success then
					local wps = path:GetWaypoints()
					local wp = wps[math.min(3, #wps)]
					if wp then
						rec.hum:MoveTo(wp.Position)
					end
				end
			end
		else
			rec.hum:MoveTo(rec.root.Position)
			if d > 0.1 then
				faceDir(rec, to)
			end
		end
		if sees then
			S.GameState.AddSuspicion(Config.Detective.WatchPerSecond * 0.4)
			if os.clock() - lastLine > 22 and d < 30 then
				lastLine = os.clock()
				S.Rig.Say(rec.model, Util.pick(Story.Lines.Detective), nil, 3)
				S.Net.Anim(rec.model, "Phone", "Action")
				task.delay(1.6, function()
					if rec.model and rec.model.Parent then
						S.Net.Anim(rec.model, false, "Action")
					end
				end)
			end
		end
		task.wait(0.4)
	end
end

-- ── Combat hooks ─────────────────────────────────────────────────────────────
function NPC.FindTarget(hrp: BasePart, range: number, cone: number)
	local best, bestD = nil, math.huge
	local look = Util.flat(hrp.CFrame.LookVector).Unit
	local cosLimit = math.cos(math.rad(cone / 2))
	for _, rec in NPC.List do
		if present(rec) and rec.state ~= "Victim" then
			local to = Util.flat(rec.root.Position - hrp.Position)
			local d = to.Magnitude
			if d <= range and d > 0.01 and look:Dot(to.Unit) >= cosLimit and d < bestD then
				best, bestD = rec, d
			end
		end
	end
	return best
end

function NPC.BeginVictim(rec)
	rec.token += 1
	rec.state = "Victim"
	setTalk(rec, false)
	NPC.Unsit(rec)
	if rec.hum then
		rec.hum:MoveTo(rec.root.Position)
	end
end

function NPC.Kill(rec, info)
	rec.alive = false
	rec.token += 1
	rec.state = "Dead"
	local model = rec.model
	setTalk(rec, false)
	local tp = rec.root and rec.root:FindFirstChild("TalkPrompt")
	if tp then
		tp:Destroy()
	end
	S.Net.StopAnim(model, "*")
	if rec.root then
		rec.root.Anchored = false
	end
	if rec.hum then
		rec.hum.Health = 0
	end
	S.Rig.Ragdoll(model)
	S.Rig.SetExpression(model, "Dead")
	model:SetAttribute("DisplayName", rec.def.name)
	model.Parent = workspace.Evidence
	-- knock the body back along the last blow
	local ut = model:FindFirstChild("UpperTorso") :: BasePart?
	if ut and info and info.dir then
		ut.AssemblyLinearVelocity = info.dir * 14 + Vector3.new(0, 6, 0)
	end
	if ut then
		S.Blood.Pool(ut.Position, 5 + (info and info.weapon and info.weapon.blood or 1) * 2, 9, { victim = rec.def.name })
		task.delay(0.35, function()
			S.Net.Sfx("BodyDrop", ut, 0.9)
		end)
	end
	S.Bodies.Setup(model)
	if info and info.weapon and info.weapon.dismember and Config.Gore().Dismember then
		task.delay(0.1, function()
			local limb = math.random() < 0.45 and "Head" or Util.pick({ "RightArm", "LeftArm", "RightLeg" })
			S.Bodies.Sever(model, limb)
		end)
	end
	S.GameState.OnVictimKilled(rec)
end

return NPC

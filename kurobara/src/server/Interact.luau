-- KUROBARA · World interactions: showers, sinks, incinerator, dumpster, garden plot,
-- home (wardrobe, diary, front door), the school gate, Nyx's desk, talking to people.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Util = require(Shared.Util)

local Interact = {}
local S

function Interact.Init(services)
	S = services
end

local function prompt(parent: Instance, action: string, object: string, hold: number?, dist: number?): ProximityPrompt
	local p = Instance.new("ProximityPrompt")
	p.ActionText = action
	p.ObjectText = object
	p.KeyboardKeyCode = Enum.KeyCode.E
	p.HoldDuration = hold or 0
	p.MaxActivationDistance = dist or 8
	p.RequiresLineOfSight = false
	p.Style = Enum.ProximityPromptStyle.Custom
	p.Parent = parent
	return p
end

local function ready(player: Player)
	local st = S.GameState.Get(player)
	if not st or st.Busy or S.GameState.Phase ~= "Playing" then
		return nil
	end
	return st
end

local function busyFor(player: Player, st, clip: string?, seconds: number)
	st.Busy = true
	local char = player.Character
	local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
	if hrp then
		hrp.Anchored = true
	end
	if clip and char then
		S.Net.Anim(char, clip, "Action")
	end
	task.wait(seconds)
	if clip and char then
		S.Net.StopAnim(char, "Action")
	end
	if hrp and S.GameState.Phase == "Playing" then
		hrp.Anchored = false
	end
	st.Busy = false
end

-- ── Cleaning ─────────────────────────────────────────────────────────────────
function Interact.Shower(player: Player, zone: BasePart)
	local st = ready(player)
	if not st or st.Dragging or st.Carrying then
		return
	end
	local head = zone.Parent and zone.Parent:FindFirstChild("ShowerHead")
	local water = head and head:FindFirstChild("Water") :: ParticleEmitter?
	if water then
		water.Enabled = true
	end
	S.Net.Sfx("Water", zone, 0.6)
	busyFor(player, st, "Shower", 3.2)
	if water then
		water.Enabled = false
	end
	local wasBloody = st.Bloodiness > 0
	S.GameState.ClearBlood(player)
	S.GameState.AddBPM(player, -8)
	S.Net.Notify(player, wasBloody and "The water runs red, then clear." or "Fresh.", "info")
end

function Interact.Sink(player: Player, basin: BasePart)
	local st = ready(player)
	if not st or st.Dragging or st.Carrying then
		return
	end
	local tool = S.Items.Equipped(player)
	if tool and tool:GetAttribute("Bloody") then
		S.Net.Sfx("Water", basin, 0.45, 1.1)
		busyFor(player, st, "Wash", 2.2)
		S.Blood.SetWeaponBloody(tool, false)
		S.Net.Notify(player, tool.Name .. " is clean. No prints, no blood.", "info")
	elseif st.Bloodiness > 0 then
		S.Net.Sfx("Water", basin, 0.45, 1.1)
		busyFor(player, st, "Wash", 1.6)
		st.Bloodiness = math.max(0, st.Bloodiness - 12)
		local stains = player.Character and player.Character:FindFirstChild("Stains")
		if stains then
			for i, s in stains:GetChildren() do
				if i > 4 then
					break
				end
				s:Destroy()
			end
		end
		S.GameState.Sync(player)
		S.Net.Notify(player, st.Bloodiness > 0 and "Hands are clean. The uniform isn't." or "Clean enough.", "info")
	else
		S.Net.Notify(player, "Equip a bloody weapon to wash it.", "info")
	end
end

-- ── Disposal spots ───────────────────────────────────────────────────────────
function Interact.Incinerator(player: Player)
	local st = ready(player)
	if not st then
		return
	end
	if S.Bodies.Dispose(player, "Incinerator") then
		return
	end
	local tool = S.Items.Equipped(player)
	if tool and tool:GetAttribute("Bloody") then
		busyFor(player, st, "Carry", 0.8)
		local name = tool.Name
		tool:Destroy()
		S.Net.Notify(player, name .. " is ash now.", "info")
	elseif st.Bloodiness > 0 then
		busyFor(player, st, "Carry", 1.2)
		S.GameState.ClearBlood(player)
		S.Net.Notify(player, "You burn the stained blazer. You always keep a spare.", "info")
	else
		S.Net.Notify(player, "Nothing to burn. Drag a body here, or bring evidence.", "info")
	end
end

function Interact.Dumpster(player: Player)
	if not ready(player) then
		return
	end
	if not S.Bodies.Dispose(player, "Dumpster") then
		S.Net.Notify(player, "Carry a trash bag here to get rid of it.", "info")
	end
end

function Interact.Dig(player: Player)
	if not ready(player) then
		return
	end
	if not S.Bodies.Dispose(player, "Bury") then
		S.Net.Notify(player, "Soft soil. Perfect for hiding something.", "info")
	end
end

-- ── Home ─────────────────────────────────────────────────────────────────────
function Interact.Wardrobe(player: Player)
	local st = ready(player)
	if not st then
		return
	end
	busyFor(player, st, nil, 0.8)
	S.GameState.ClearBlood(player)
	S.Net.Notify(player, "Fresh uniform. Nobody will ever know.", "info")
end

function Interact.Diary(player: Player)
	local st = ready(player)
	if not st then
		return
	end
	busyFor(player, st, "Wash", 1.4)
	st.BPM = Config.BPM.Base
	S.GameState.Sync(player)
	S.GameState.RefreshMood(player)
	S.Net.Notify(player, "You write his name until your hands stop shaking.", "love")
end

function Interact.HomeDoor(player: Player)
	local st = ready(player)
	if not st or st.Dragging or st.Carrying then
		return
	end
	if S.Clock.Minutes >= 17 * 60 then
		S.Net.Notify(player, "School's over for today.", "info")
		return
	end
	st.Busy = true
	S.Net.Screen(player, "Fade", { time = 1 })
	task.wait(0.5)
	if player.Character then
		player.Character:PivotTo(S.World.SchoolArrive)
	end
	st.Busy = false
end

function Interact.Gate(player: Player)
	local st = ready(player)
	if not st then
		return
	end
	if S.Clock.Minutes < 15 * 60 + 30 then
		S.Net.Notify(player, "Skipping school? Haru is in there.", "info")
		return
	end
	if st.Dragging or st.Carrying then
		S.Net.Notify(player, "Not with that.", "warn")
		return
	end
	task.spawn(S.GameState.EndDay, "Home")
end

-- ── Class ────────────────────────────────────────────────────────────────────
function Interact.Desk(player: Player)
	local st = ready(player)
	if not st then
		return
	end
	local period = S.Clock.Period()
	if not period.class then
		S.Net.Notify(player, "Class hasn't started. Your seat will wait.", "info")
		return
	end
	if st.Bloodiness >= 10 or S.Items.BloodyWeaponOn(player) or st.Dragging or st.Carrying then
		S.Net.Notify(player, "Not like this. Clean up first.", "warn")
		return
	end
	st.Busy = true
	S.Net.Screen(player, "Fade", { time = 1.6, text = "Class…" })
	task.wait(0.8)
	S.Clock.Set(S.Clock.NextPeriodStart())
	S.GameState.AddRep(player, 2)
	S.GameState.AddBPM(player, -6)
	st.Busy = false
end

-- ── Talking (dialogue with choices) ─────────────────────────────────────────
local Remotes = ReplicatedStorage:WaitForChild("Remotes")

local function lastMissing(): string?
	local k = S.GameState.Killed
	return k[#k]
end

local function whereIs(rec): string
	local W = Story.Dialogue.Where
	if not rec or not rec.alive or not S.NPC.Present(rec) then
		return W.Away
	end
	local key = rec.targetKey or ""
	local best, bestLen = nil, 0
	for group, text in W do
		if key:sub(1, #group) == group and #group > bestLen then
			best, bestLen = text, #group
		end
	end
	return best or "somewhere around school"
end

function Interact.Options(player: Player, rec)
	local opts = {}
	local function add(id: string, text: string)
		table.insert(opts, { id = id, text = text })
	end
	local missing = lastMissing()
	if rec.isHaru then
		add("hi", "Hi, Haru.")
		add("haruCompliment", "I like the way you read by the fountain.")
		add("book", "What are you reading?")
		add("haruMirei", "Are you and Mirei close?")
	elseif rec.isRival then
		add("chat", "How are you?")
		add("friday", "Big plans on Friday?")
		if rec.followUntil then
			add("wait", "Wait here.")
		else
			add("follow", "Can you help me with something?")
		end
	elseif rec.role == "Teacher" then
		add("chat", "Good day, sensei.")
		if missing then
			add("missing", "Any news about " .. missing .. "?")
		end
	elseif rec.def.persona == "Detective" then
		add("chat", "Hey, Ryo.")
		add("case", "What are you investigating?")
	else
		add("chat", "How's it going?")
		add("compliment", "I like your hair today.")
		add("haru", "Have you seen Haru?")
		add("mirei", "What's Mirei up to?")
		if missing then
			add("missing", "Did you hear about " .. missing .. "?")
		end
		if rec.followUntil then
			add("wait", "Wait here.")
		else
			add("follow", "Can you come with me?")
		end
	end
	add("bye", "Bye.")
	return opts
end

function Interact.SendDialogue(player: Player, rec, line: string)
	S.Net.Anim(rec.model, "Talk", "Action")
	S.Rig.Say(rec.model, line, nil, 4.5)
	Remotes.Dialogue:FireClient(player, {
		name = rec.def.name,
		line = line,
		options = Interact.Options(player, rec),
		love = rec.isHaru == true,
	})
end

function Interact.CloseDialogue(player: Player)
	local st = S.GameState.Get(player)
	local rec = st and st.Dialogue
	if st then
		st.Dialogue = nil
	end
	if rec then
		rec.inDialogue = false
		rec.talking = false
		rec.targetKey = nil
	end
	Remotes.Dialogue:FireClient(player, nil)
end

function Interact.Talk(player: Player, rec)
	local st = ready(player)
	if not st or rec.state ~= "Routine" or not rec.alive then
		return
	end
	if st.Dialogue and st.Dialogue ~= rec then
		Interact.CloseDialogue(player)
	end
	st.Dialogue = rec
	rec.inDialogue = true
	rec.talking = true
	rec.token += 1
	rec.targetKey = nil
	local char = player.Character
	local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
	if hrp and not rec.sitting then
		local d = Util.flat(hrp.Position - rec.root.Position)
		if d.Magnitude > 0.1 then
			rec.hum:MoveTo(rec.root.Position)
			rec.root.CFrame = CFrame.lookAt(rec.root.Position, rec.root.Position + d.Unit)
		end
	end
	local D = Story.Dialogue
	local greet = rec.isHaru and Util.pick(D.GreetHaru) or rec.isRival and Util.pick(D.GreetMirei)
		or rec.role == "Teacher" and Util.pick(D.GreetTeacher) or rec.def.persona == "Detective" and Util.pick(D.GreetRyo)
		or Util.pick(D.Greet)
	Interact.SendDialogue(player, rec, greet)
	-- close when she walks off, after a minute, or if anything happens to them
	task.spawn(function()
		local t0 = os.clock()
		while st.Dialogue == rec do
			task.wait(0.5)
			local h = player.Character and player.Character:FindFirstChild("HumanoidRootPart") :: BasePart?
			local far = not h or not rec.root or (h.Position - rec.root.Position).Magnitude > 14
			if far or os.clock() - t0 > 60 or not rec.alive or rec.state ~= "Routine" or S.GameState.Phase ~= "Playing" then
				Interact.CloseDialogue(player)
			end
		end
	end)
end

function Interact.Choose(player: Player, id: string)
	local st = S.GameState.Get(player)
	local rec = st and st.Dialogue
	if not rec or not rec.alive or not S.NPC.Present(rec) then
		Interact.CloseDialogue(player)
		return
	end
	local D = Story.Dialogue
	local missing = lastMissing() or "them"
	local today = S.GameState.Day .. ":" .. id .. ":" .. rec.def.id
	local first = not st.LastTalk[today]
	st.LastTalk[today] = true
	local line
	local closeAfter = false
	if id == "bye" then
		S.Rig.Say(rec.model, Util.pick(D.Bye), nil, 2)
		Interact.CloseDialogue(player)
		return
	elseif id == "chat" then
		if rec.isRival then
			line = Util.pick(Story.Lines.Mirei)
		elseif rec.role == "Teacher" then
			line = Util.pick(Story.Lines.Teacher)
		elseif rec.def.persona == "Detective" then
			line = Util.pick(S.GameState.Investigating and Story.Lines.Detective or Story.Lines.DetectiveTalk)
		else
			line = Util.pick(Story.Lines.Talk)
		end
		if first then
			S.GameState.AddRep(player, 1)
		end
	elseif id == "compliment" then
		if st.BPM >= Config.BPM.Unstable or st.Bloodiness >= 20 then
			line = Util.pick(D.ComplimentCreepy)
			S.GameState.AddRep(player, -3)
		else
			line = Util.pick(D.ComplimentOk)
			if first then
				S.GameState.AddRep(player, 2)
			end
		end
	elseif id == "haru" then
		line = "I think he's " .. whereIs(S.NPC.Get("Haru")) .. "."
	elseif id == "mirei" then
		line = "She's " .. whereIs(S.NPC.Get("Mirei")) .. "." .. (S.GameState.RivalDown and "" or " And she won't shut up about Friday.")
	elseif id == "missing" then
		line = (Util.pick(rec.role == "Teacher" and D.TeacherMissing or D.AskMissing):gsub("{missing}", missing))
	elseif id == "follow" then
		local tool = S.Items.Equipped(player)
		local scary = st.Reputation < -25 or st.Bloodiness >= 15 or st.BPM >= Config.BPM.Unstable or (tool and (tool:GetAttribute("Suspicion") or 0) > 0.3)
		if scary then
			line = Util.pick(D.FollowNo)
		else
			line = Util.pick(D.FollowYes)
			S.NPC.StartFollowing(rec)
			closeAfter = true
		end
	elseif id == "wait" then
		line = Util.pick(D.Wait)
		S.NPC.StopFollowing(rec)
		closeAfter = true
	elseif id == "hi" then
		local lines = Story.Lines.Haru
		line = lines[math.clamp(math.floor(st.Affection / 8) + 1, 1, #lines)]
		local key = S.GameState.Day .. ":" .. S.Clock.Period().id .. ":haru"
		if not st.LastTalk[key] then
			st.LastTalk[key] = true
			Interact.AddAffection(player, st, 6)
		end
	elseif id == "haruCompliment" then
		line = Util.pick(D.HaruCompliment)
		S.Rig.SetExpression(rec.model, "Smile")
		if first then
			Interact.AddAffection(player, st, 5)
		end
		S.GameState.AddBPM(player, -8)
	elseif id == "book" then
		line = Util.pick(D.HaruBook)
	elseif id == "haruMirei" then
		line = Util.pick(D.HaruMirei)
		S.GameState.AddBPM(player, 6)
	elseif id == "friday" then
		line = Util.pick(D.MireiFriday)
		S.GameState.AddBPM(player, 5)
	elseif id == "case" then
		line = Util.pick(D.RyoCase)
		if S.GameState.Investigating then
			S.GameState.AddSuspicion(2)
		end
	else
		return
	end
	S.GameState.Sync(player)
	Interact.SendDialogue(player, rec, line)
	if closeAfter then
		task.delay(1.4, function()
			if st.Dialogue == rec then
				Interact.CloseDialogue(player)
			end
		end)
	end
end

function Interact.AddAffection(player: Player, st, n: number)
	local before = st.Affection
	st.Affection += n
	S.GameState.AddBPM(player, -6)
	S.Net.Notify(player, "♥ Haru noticed you. (" .. math.floor(st.Affection) .. ")", "love")
	if before < 20 and st.Affection >= 20 then
		S.GameState.Text(player, "Haru")
	end
end

-- ── Wire up the world ────────────────────────────────────────────────────────
function Interact.Setup()
	local W = S.World
	for _, zone in W.Interact.Showers do
		prompt(zone, "Shower", "Shower", 0.4).Triggered:Connect(function(player)
			Interact.Shower(player, zone)
		end)
	end
	for _, basin in W.Interact.Sinks do
		prompt(basin, "Wash", "Sink", 0.3, 6).Triggered:Connect(function(player)
			Interact.Sink(player, basin)
		end)
	end
	if W.Interact.Incinerator then
		prompt(W.Interact.Incinerator, "Use", "Incinerator", 0.5, 10).Triggered:Connect(Interact.Incinerator)
	end
	if W.Interact.Dumpster then
		prompt(W.Interact.Dumpster, "Throw away", "Dumpster", 0.3, 9).Triggered:Connect(Interact.Dumpster)
	end
	if W.Interact.DigPlot then
		prompt(W.Interact.DigPlot, "Dig", "Garden plot", 0.5, 9).Triggered:Connect(Interact.Dig)
	end
	if W.Interact.Wardrobe then
		prompt(W.Interact.Wardrobe, "Change clothes", "Wardrobe", 0.4).Triggered:Connect(Interact.Wardrobe)
	end
	if W.Interact.Diary then
		prompt(W.Interact.Diary, "Write", "Diary", 0.4).Triggered:Connect(Interact.Diary)
	end
	if W.Interact.HomeDoor then
		prompt(W.Interact.HomeDoor, "Walk to school", "Front door", 0, 9).Triggered:Connect(Interact.HomeDoor)
	end
	if W.Interact.Gate then
		prompt(W.Interact.Gate, "Go home", "School gate", 1, 9).Triggered:Connect(Interact.Gate)
	end
	local seat = S.NPC.PlayerSeat()
	if seat and seat.chair then
		prompt(seat.chair, "Attend class", "Your desk", 0.5, 7).Triggered:Connect(Interact.Desk)
		seat.chair.Color = Color3.fromRGB(150, 40, 60) -- Nyx's chair has a red cushion
	end
end

return Interact

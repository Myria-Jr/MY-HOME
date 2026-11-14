-- KUROBARA · Game flow: player stats, police, day transitions, game over, endings

local CollectionService = game:GetService("CollectionService")
local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Util = require(Shared.Util)

local GameState = {}
GameState.Phase = "Title" -- Title | Playing | Transition | Over | Ending
GameState.Day = 1
GameState.RivalDown = false
GameState.PoliceETA = nil :: number?
GameState.PhotoEvidence = false
GameState.Killed = {} :: { string }
local S
local states: { [Player]: any } = {}

function GameState.Init(services)
	S = services
end

local function newState()
	return {
		BPM = Config.BPM.Base,
		Reputation = 0,
		Bloodiness = 0,
		Affection = 0,
		Busy = false,
		Attacking = false,
		Dismembering = false,
		Mopping = false,
		Dragging = nil,
		Carrying = nil,
		CarryKind = nil,
		Crouching = false,
		Running = false,
		LastTalk = {},
		NextLaugh = 0,
		BaseClip = nil,
		MoodClip = nil,
		Expression = nil,
		NearHaru = false,
	}
end

function GameState.Get(player: Player)
	return states[player]
end

function GameState.MainPlayer(): (Player?, any)
	for _, p in Players:GetPlayers() do
		if states[p] then
			return p, states[p]
		end
	end
	return nil, nil
end

function GameState.AddPlayer(player: Player)
	states[player] = newState()
	GameState.Sync(player)
end

function GameState.RemovePlayer(player: Player)
	states[player] = nil
end

function GameState.Sync(player: Player)
	local st = states[player]
	if not st then
		return
	end
	player:SetAttribute("BPM", math.floor(st.BPM + 0.5))
	player:SetAttribute("Reputation", math.floor(st.Reputation + 0.5))
	player:SetAttribute("Bloodiness", math.floor(st.Bloodiness + 0.5))
	player:SetAttribute("Affection", math.floor(st.Affection + 0.5))
	player:SetAttribute("Dragging", st.Dragging ~= nil or st.Carrying ~= nil)
	workspace:SetAttribute("Day", GameState.Day)
	workspace:SetAttribute("DayName", Config.Days[GameState.Day])
	workspace:SetAttribute("Phase", GameState.Phase)
	workspace:SetAttribute("Objective", GameState.RivalDown and "Mirei is gone. Don't get caught." or Story.Objectives[GameState.Day])
end

-- ── Stats ────────────────────────────────────────────────────────────────────
function GameState.AddBPM(player: Player, n: number)
	local st = states[player]
	if st then
		st.BPM = Util.clamp(st.BPM + n, 60, Config.BPM.Max)
		GameState.Sync(player)
	end
end

function GameState.AddRep(player: Player, n: number)
	local st = states[player]
	if not st then
		return
	end
	st.Reputation = Util.clamp(st.Reputation + n, Config.Reputation.Min, Config.Reputation.Max)
	GameState.Sync(player)
	if st.Reputation <= Config.Reputation.Min then
		GameState.GameOver(player, "Expelled")
	end
end

function GameState.AddBloodiness(player: Player, n: number)
	local st = states[player]
	if st then
		st.Bloodiness = Util.clamp(st.Bloodiness + n, 0, 100)
		GameState.Sync(player)
	end
end

function GameState.ClearBlood(player: Player)
	local st = states[player]
	if st then
		st.Bloodiness = 0
	end
	if player.Character then
		S.Blood.ClearStains(player.Character)
	end
	GameState.Sync(player)
end

-- Movement speed, layered animations and Nyx's face, derived from her state
function GameState.RefreshMood(player: Player)
	local st = states[player]
	local char = player.Character
	local hum = char and char:FindFirstChildOfClass("Humanoid")
	if not st or not hum then
		return
	end
	local P = Config.Player
	if st.Dragging then
		hum.WalkSpeed = P.DragSpeed
	elseif st.Carrying then
		hum.WalkSpeed = P.CarrySpeed
	elseif st.Crouching then
		hum.WalkSpeed = P.CrouchSpeed
	elseif st.Running then
		hum.WalkSpeed = P.RunSpeed
	else
		hum.WalkSpeed = P.WalkSpeed
	end
	local base = st.Dragging and "Drag" or (st.Carrying and "Carry") or (st.Crouching and "Crouch") or nil
	if base ~= st.BaseClip then
		st.BaseClip = base
		if base then
			S.Net.Anim(char, base, "Base")
		else
			S.Net.StopAnim(char, "Base")
		end
	end
	local mood = st.BPM >= Config.BPM.Unstable and "Insane" or nil
	if mood ~= st.MoodClip then
		st.MoodClip = mood
		if mood then
			S.Net.Anim(char, mood, "Mood")
		else
			S.Net.StopAnim(char, "Mood")
		end
	end
	if not st.Attacking and not st.Dismembering then
		local expr = "Neutral"
		if st.BPM >= Config.BPM.Unstable then
			expr = "Psycho"
		elseif st.NearHaru then
			expr = "Smile"
		end
		if expr ~= st.Expression then
			st.Expression = expr
			S.Rig.SetExpression(char, expr)
		end
		S.Rig.SetBlush(char, st.NearHaru)
	else
		st.Expression = "Psycho"
	end
end

function GameState.Seen(player: Player, rec, kind: string)
	S.Net.Screen(player, "Seen", { name = rec.def.name, kind = kind })
end

function GameState.OnVictimKilled(rec)
	table.insert(GameState.Killed, rec.def.name)
	local player = GameState.MainPlayer()
	if rec.isRival then
		GameState.RivalDown = true
		if player then
			S.Net.Notify(player, "Mirei won't be confessing to anyone.", "love")
		end
	end
	if player then
		GameState.Sync(player)
	end
end

function GameState.MarkGone(_name: string, _model: Instance?) end

-- ── Police ───────────────────────────────────────────────────────────────────
function GameState.CallPolice(reason: string)
	if GameState.Phase ~= "Playing" or GameState.PoliceETA then
		return
	end
	GameState.PoliceETA = workspace:GetServerTimeNow() + Config.Police.ArrivalSeconds
	workspace:SetAttribute("PoliceETA", GameState.PoliceETA)
	S.Net.Notify(nil, reason, "warn")
	S.Net.Notify(nil, "🚨 The police are on their way.", "warn")
end

local function livingWitness()
	for _, rec in S.NPC.List do
		if rec.alive and rec.witness then
			return rec
		end
	end
	return nil
end

function GameState.PoliceArrive()
	GameState.PoliceETA = nil
	workspace:SetAttribute("PoliceETA", nil)
	local player, st = GameState.MainPlayer()
	if not player or not st then
		return
	end
	local reason
	local w = livingWitness()
	if w then
		reason = w.def.name .. " told the police exactly what they saw."
	elseif GameState.PhotoEvidence then
		reason = "A student handed the police a video of you."
	elseif st.Bloodiness >= 15 then
		reason = "The officers noticed the blood on your uniform."
	elseif S.Items.BloodyWeaponOn(player) then
		reason = "They found a bloody weapon in your bag."
	elseif st.Dragging or st.Carrying then
		reason = "They caught you with a body."
	end
	if reason then
		GameState.GameOver(player, "Arrested", reason)
	else
		GameState.EndDay("Police")
	end
end

-- ── Flow ─────────────────────────────────────────────────────────────────────
local function countEvidence(): (number, number)
	local bodies = 0
	for _, tag in { "Body", "BodyPart", "Bag" } do
		for _, m in CollectionService:GetTagged(tag) do
			if m:IsDescendantOf(workspace) then
				bodies += 1
			end
		end
	end
	local pools = 0
	for _, p in CollectionService:GetTagged("BloodPool") do
		if p:IsDescendantOf(workspace) and (p:GetAttribute("Size") or 0) >= 1.5 then
			pools += 1
		end
	end
	return bodies, pools
end

local function clearEvidence()
	for _, m in workspace.Evidence:GetChildren() do
		if m.Name ~= "Blood" then
			m:Destroy()
		end
	end
	S.Blood.ClearAll()
end

function GameState.SpawnPlayerAtHome(player: Player)
	local char = player.Character
	if char then
		local hrp = char:FindFirstChild("HumanoidRootPart") :: BasePart?
		if hrp then
			hrp.Anchored = false
		end
		char:PivotTo(S.World.HomeSpawn)
	end
end

function GameState.StartDay()
	local player = GameState.MainPlayer()
	S.Clock.Set(Config.Time.DayStart)
	GameState.Phase = "Playing"
	S.NPC.SetPaused(false)
	S.Clock.Running = true
	if player then
		GameState.Sync(player)
		S.Net.Screen(player, "DayStart", {
			day = GameState.Day,
			dayName = Config.Days[GameState.Day],
			objective = Story.Objectives[GameState.Day],
		})
	end
end

function GameState.EndDay(reason: string)
	if GameState.Phase ~= "Playing" then
		return
	end
	GameState.Phase = "Transition"
	S.Clock.Running = false
	S.NPC.SetPaused(true)
	local player, st = GameState.MainPlayer()
	if player and st then
		S.Bodies.StopDrag(player)
		S.Bodies.StopCarry(player)
	end
	local bodies, pools = countEvidence()
	local lines = {}
	if reason == "Police" then
		table.insert(lines, "The police searched the school until dark.")
		table.insert(lines, bodies > 0 and "They found what you left behind… but no suspect." or "They found nothing. Not yet.")
	elseif bodies > 0 then
		local w = livingWitness()
		if (w or GameState.PhotoEvidence) and player then
			GameState.Phase = "Playing"
			GameState.GameOver(player, "Arrested", "Overnight, the janitor found a body. A witness came forward.")
			return
		end
		table.insert(lines, "Overnight, the janitor found something terrible.")
		table.insert(lines, "The police have no suspects. Yet.")
	elseif pools > 0 then
		table.insert(lines, "The janitor scrubbed strange stains off the floor tonight.")
	else
		table.insert(lines, reason == "Home" and "You walked home early. Haru's face stayed with you the whole way." or "Another ordinary day at Kurobara Academy.")
	end
	if #GameState.Killed > 0 then
		table.insert(lines, "Missing: " .. table.concat(GameState.Killed, ", "))
	end
	clearEvidence()
	GameState.PoliceETA = nil
	workspace:SetAttribute("PoliceETA", nil)
	GameState.PhotoEvidence = false

	if GameState.RivalDown then
		GameState.Ending((st and st.Affection or 0) >= 25 and "Good" or "Neutral")
		return
	end
	if GameState.Day >= #Config.Days then
		GameState.Ending("Bad")
		return
	end

	GameState.Day += 1
	if player and st then
		GameState.ClearBlood(player) -- she washes at home overnight (weapons stay bloody!)
		st.BPM = math.max(Config.BPM.Base, st.BPM - 45)
		S.Net.Screen(player, "Night", { lines = lines, day = GameState.Day, dayName = Config.Days[GameState.Day] })
	end
	task.wait(5.5)
	S.NPC.NewDay()
	S.Items.RespawnMissing()
	if player then
		GameState.SpawnPlayerAtHome(player)
	end
	GameState.Phase = "Transition"
	GameState.StartDay()
end

function GameState.GameOver(player: Player, kind: string, extra: string?)
	if GameState.Phase ~= "Playing" then
		return
	end
	GameState.Phase = "Over"
	S.Clock.Running = false
	S.NPC.SetPaused(true)
	local info = Story.GameOver[kind] or { title = "GAME OVER", text = "" }
	local char = player.Character
	local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
	if hrp then
		hrp.Anchored = true
	end
	GameState.Sync(player)
	S.Net.Screen(player, "GameOver", { title = info.title, text = info.text, extra = extra or "" })
end

function GameState.Ending(kind: string)
	if GameState.Phase == "Over" or GameState.Phase == "Ending" then
		return
	end
	GameState.Phase = "Ending"
	S.Clock.Running = false
	S.NPC.SetPaused(true)
	local info = Story.Endings[kind]
	local player = GameState.MainPlayer()
	if player then
		GameState.Sync(player)
	end
	S.Net.Screen(nil, "Ending", { kind = kind, title = info.title, lines = info.lines })
end

function GameState.Start(player: Player)
	if GameState.Phase ~= "Title" then
		return
	end
	GameState.Phase = "Transition"
	S.SpawnCharacter(player)
	GameState.SpawnPlayerAtHome(player)
	GameState.StartDay()
end

function GameState.Restart(player: Player)
	if GameState.Phase ~= "Over" and GameState.Phase ~= "Ending" then
		return
	end
	GameState.Phase = "Transition"
	S.Net.Screen(player, "Fade", { time = 1.2 })
	task.wait(0.6)
	clearEvidence()
	S.NPC.ResetAll()
	S.Items.ResetAll()
	GameState.Day = 1
	GameState.RivalDown = false
	GameState.PoliceETA = nil
	GameState.PhotoEvidence = false
	GameState.Killed = {}
	workspace:SetAttribute("PoliceETA", nil)
	states[player] = newState()
	S.SpawnCharacter(player)
	GameState.SpawnPlayerAtHome(player)
	GameState.StartDay()
end

-- ── Per-frame upkeep ─────────────────────────────────────────────────────────
function GameState.Start_Loop()
	local acc = 0
	local lastMinutes = S.Clock.Minutes
	RunService.Heartbeat:Connect(function(dt)
		acc += dt
		if acc < 0.25 then
			return
		end
		acc = 0
		if GameState.Phase ~= "Playing" then
			lastMinutes = S.Clock.Minutes
			return
		end
		local dm = math.max(0, S.Clock.Minutes - lastMinutes)
		lastMinutes = S.Clock.Minutes
		if GameState.PoliceETA and workspace:GetServerTimeNow() >= GameState.PoliceETA then
			GameState.PoliceETA = nil
			task.spawn(GameState.PoliceArrive)
			return
		end
		for player, st in states do
			local char = player.Character
			local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
			if hrp then
				local haru = S.NPC.Get("Haru")
				st.NearHaru = haru ~= nil and S.NPC.Present(haru) and (haru.root.Position - hrp.Position).Magnitude < 22
				local calm = Config.BPM.DecayPerMinute + (st.NearHaru and Config.BPM.HaruCalmPerMinute or 0)
				if st.BPM > Config.BPM.Base then
					st.BPM = math.max(Config.BPM.Base, st.BPM - calm * dm)
				end
				-- breakdown: involuntary laughter that everyone nearby hears
				if st.BPM >= Config.BPM.Breakdown and os.clock() > st.NextLaugh and not st.Busy then
					st.NextLaugh = os.clock() + math.random(14, 26)
					S.Net.Anim(char, "Laugh", "Action")
					S.Rig.Say(char, Util.pick({ "hehe… hahaha…", "ahaha— shh. shh.", "hahahaha" }), Config.UI.Accent, 2.5)
					S.NPC.Scream(hrp.Position, nil)
				end
				GameState.RefreshMood(player)
			end
			GameState.Sync(player)
		end
	end)
end

return GameState

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

-- ── Talking ──────────────────────────────────────────────────────────────────
function Interact.Talk(player: Player, rec)
	local st = ready(player)
	if not st or rec.state ~= "Routine" or not rec.alive then
		return
	end
	local char = player.Character
	local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
	if hrp and not rec.sitting then
		local d = Util.flat(hrp.Position - rec.root.Position)
		if d.Magnitude > 0.1 then
			rec.root.CFrame = CFrame.lookAt(rec.root.Position, rec.root.Position + d.Unit)
		end
	end
	S.Net.Anim(rec.model, "Talk", "Action")
	local periodKey = S.GameState.Day .. ":" .. S.Clock.Period().id
	local first = st.LastTalk[rec.def.id] ~= periodKey
	st.LastTalk[rec.def.id] = periodKey
	if rec.isHaru then
		local lines = Story.Lines.Haru
		local idx = math.clamp(math.floor(st.Affection / 8) + 1, 1, #lines)
		S.Rig.Say(rec.model, lines[idx], nil, 4)
		S.Rig.SetExpression(rec.model, "Smile")
		task.delay(4, function()
			if rec.alive then
				S.Rig.SetExpression(rec.model, "Neutral")
			end
		end)
		if first then
			st.Affection += 6
			S.GameState.AddBPM(player, -10)
			S.Net.Notify(player, "♥ Haru noticed you. (" .. math.floor(st.Affection) .. ")", "love")
		end
	elseif rec.isRival then
		S.Rig.Say(rec.model, Util.pick(Story.Lines.Mirei), nil, 4)
	elseif rec.def.persona == "Detective" then
		if S.GameState.Investigating then
			S.Rig.Say(rec.model, Util.pick(Story.Lines.Detective), nil, 4)
			S.GameState.AddSuspicion(first and 4 or 1)
		else
			S.Rig.Say(rec.model, Util.pick(Story.Lines.DetectiveTalk), nil, 4)
		end
	elseif rec.role == "Teacher" then
		S.Rig.Say(rec.model, Util.pick(Story.Lines.Teacher), nil, 3.5)
	else
		S.Rig.Say(rec.model, Util.pick(Story.Lines.Talk), nil, 4)
		if first then
			S.GameState.AddRep(player, 1)
		end
	end
	S.GameState.Sync(player)
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

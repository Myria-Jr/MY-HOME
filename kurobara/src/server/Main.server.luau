-- KUROBARA · Server entry point

local Players = game:GetService("Players")
local PhysicsService = game:GetService("PhysicsService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Remotes = ReplicatedStorage:WaitForChild("Remotes")

local here = script.Parent
local S = {}
S.Builder = require(here.Builder)
S.Rig = require(here.Rig)
S.Net = require(here.Net)
S.Blood = require(here.Blood)
S.Items = require(here.ItemService)
S.Combat = require(here.Combat)
S.Bodies = require(here.Bodies)
S.NPC = require(here.NPCService)
S.Clock = require(here.Clock)
S.GameState = require(here.GameState)
S.Interact = require(here.Interact)

Players.CharacterAutoLoads = false

-- ── Collision groups ─────────────────────────────────────────────────────────
for _, g in { "NPC", "Ragdoll", "Player", "BloodRay" } do
	if not PhysicsService:IsCollisionGroupRegistered(g) then
		PhysicsService:RegisterCollisionGroup(g)
	end
end
PhysicsService:CollisionGroupSetCollidable("NPC", "NPC", false)
PhysicsService:CollisionGroupSetCollidable("Ragdoll", "Player", false)
PhysicsService:CollisionGroupSetCollidable("Ragdoll", "NPC", false)
PhysicsService:CollisionGroupSetCollidable("BloodRay", "NPC", false)
PhysicsService:CollisionGroupSetCollidable("BloodRay", "Player", false)
PhysicsService:CollisionGroupSetCollidable("BloodRay", "Ragdoll", false)

-- ── World ────────────────────────────────────────────────────────────────────
S.World = S.Builder.Build()

for _, name in { "Blood", "Items", "Combat", "Bodies", "NPC", "Clock", "GameState", "Interact" } do
	local m = S[name]
	if m.Init then
		m.Init(S)
	end
end

-- ── The player character: always Nyx ─────────────────────────────────────────
function S.SpawnCharacter(player: Player)
	local look = S.Rig.LookFor(Story.Protagonist.Look)
	look.name = Story.Protagonist.Name
	local desc = S.Rig.Description(look)
	player:LoadCharacterWithHumanoidDescription(desc)
	local char = player.Character or player.CharacterAdded:Wait()
	local hum = char:WaitForChild("Humanoid") :: Humanoid
	char:WaitForChild("HumanoidRootPart")
	char:WaitForChild("Head")
	char:WaitForChild("UpperTorso")
	S.Rig.Dress(char, look, "Student")
	hum.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None
	hum.HealthDisplayType = Enum.HumanoidHealthDisplayType.AlwaysOff
	hum.BreakJointsOnDeath = false
	hum.UseJumpPower = true
	hum.JumpPower = 0
	hum.WalkSpeed = Config.Player.WalkSpeed
	for _, p in char:GetDescendants() do
		if p:IsA("BasePart") and p.Parent == char then
			p.CollisionGroup = "Player"
		end
	end
	char:SetAttribute("IsNyx", true)
	char:SetAttribute("Gender", "F")
	char:SetAttribute("VoiceId", Config.Voice.Nyx.id)
	char:SetAttribute("VoicePitch", Config.Voice.Nyx.pitch)
	char:SetAttribute("VoiceSpeed", Config.Voice.Nyx.speed)
	local st = S.GameState.Get(player)
	if st then
		st.BaseClip, st.MoodClip, st.Expression = nil, nil, nil
		st.Dragging, st.Carrying, st.Busy = nil, nil, false
	end
	return char
end

-- ── Players ──────────────────────────────────────────────────────────────────
Players.PlayerAdded:Connect(function(player)
	S.GameState.AddPlayer(player)
end)
for _, p in Players:GetPlayers() do
	S.GameState.AddPlayer(p)
end
Players.PlayerRemoving:Connect(function(player)
	S.GameState.RemovePlayer(player)
end)

Remotes.ClientAction.OnServerEvent:Connect(function(player, action, value)
	local st = S.GameState.Get(player)
	if not st then
		return
	end
	if action == "Start" then
		S.GameState.Start(player)
	elseif action == "Restart" then
		S.GameState.Restart(player)
	elseif action == "Crouch" then
		st.Crouching = value == true
		S.GameState.RefreshMood(player)
	elseif action == "Run" then
		st.Running = value == true
		if st.Running then
			st.Crouching = false
		end
		S.GameState.RefreshMood(player)
	end
end)

-- ── Go ───────────────────────────────────────────────────────────────────────
S.NPC.SpawnAll()
S.NPC.SocialLoop()
S.Interact.Setup()
S.Items.RespawnMissing()
S.Clock.Set(Config.Time.DayStart)
S.Clock.Start()
S.GameState.Start_Loop()
S.Blood.StartTracking()
workspace:SetAttribute("Phase", "Title")
workspace:SetAttribute("GorePreset", Config.GorePreset)
workspace:SetAttribute("Ready", true)
print("[Kurobara] world ready ·", #S.NPC.List, "people at school · gore:", Config.GorePreset)

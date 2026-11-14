-- KUROBARA · Combat: weapon hooks and choreographed kill sequences

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Items = require(Shared.Items)
local Config = require(Shared.Config)
local Story = require(Shared.Story)
local Util = require(Shared.Util)

local Combat = {}
local S

function Combat.Init(services)
	S = services
end

function Combat.Hook(tool: Tool)
	tool.Activated:Connect(function()
		local char = tool.Parent
		local player = char and Players:GetPlayerFromCharacter(char)
		if player then
			Combat.Attack(player, tool)
		end
	end)
	tool.Equipped:Connect(function()
		local char = tool.Parent
		S.Net.Sfx("Unsheath", char and char:FindFirstChild("Head"), 0.4)
	end)
end

local function focusPoint(model: Model, focus: string): Vector3?
	local ut = model:FindFirstChild("UpperTorso") :: BasePart?
	if focus == "Head" then
		local h = model:FindFirstChild("Head") :: BasePart?
		return h and h.Position or (ut and ut.Position)
	elseif focus == "Neck" and ut then
		return (ut.CFrame * CFrame.new(0, ut.Size.Y / 2 + 0.1, -0.2)).Position
	end
	return ut and (ut.CFrame * CFrame.new(0, 0.1, -ut.Size.Z / 2)).Position
end

function Combat.Attack(player: Player, tool: Tool)
	local st = S.GameState.Get(player)
	if not st or st.Busy or st.Dragging or st.Carrying or S.GameState.Phase ~= "Playing" then
		return
	end
	local char = player.Character
	local hrp = char and char:FindFirstChild("HumanoidRootPart") :: BasePart?
	if not hrp then
		return
	end
	local def = Items.Weapons[tool:GetAttribute("ItemId")]
	if not def then
		return
	end
	local target = S.NPC.FindTarget(hrp, Config.Kill.Range, Config.Kill.ConeDegrees)
	if not target then
		st.Busy = true
		S.Net.Anim(char, "Swing", "Action")
		S.Net.Sfx("Slash", hrp, 0.45)
		task.wait(0.6)
		st.Busy = false
		return
	end
	if target.isHaru then
		S.Net.Notify(player, "…No. Never him.", "love")
		return
	end
	if target.role == "Teacher" then
		Combat.TeacherCounter(player, st, target)
		return
	end
	if target.def.persona == "Detective" and S.NPC.SeesPlayer(target) then
		-- he saw it coming
		st.Busy = true
		S.Net.Anim(char, "Swing", "Action")
		S.Net.Sfx("Slash", hrp, 0.5, 1.1)
		S.NPC.Dodge(target)
		task.wait(0.6)
		st.Busy = false
		return
	end
	Combat.KillSequence(player, st, tool, def, target)
end

function Combat.TeacherCounter(player: Player, st, rec)
	st.Busy = true
	local char = player.Character
	S.NPC.BeginVictim(rec)
	S.Net.Anim(char, "Swing", "Action")
	S.Net.Anim(rec.model, "Arrest", "Action")
	S.Rig.Say(rec.model, "I don't think so, Amane.", Config.UI.Warn, 3)
	task.wait(0.9)
	S.GameState.GameOver(player, "Apprehended", rec.def.name .. " disarmed you in one move.")
end

function Combat.KillSequence(player: Player, st, tool: Tool, def, rec)
	local char = player.Character :: Model
	local hum = char:FindFirstChildOfClass("Humanoid") :: Humanoid
	local hrp = char:FindFirstChild("HumanoidRootPart") :: BasePart
	local vm = rec.model :: Model
	local vroot = rec.root :: BasePart
	if not (hum and hrp and vm and vroot) then
		return
	end

	st.Busy = true
	st.Attacking = true
	local wasAware = rec.state ~= "Routine" or os.clock() < (rec.alertUntil or 0)
	S.NPC.BeginVictim(rec)

	-- line the two of them up
	local dir = Util.flat(vroot.Position - hrp.Position)
	if dir.Magnitude < 0.1 then
		dir = Util.flat(hrp.CFrame.LookVector)
	end
	dir = dir.Unit
	local stealth = (not wasAware) and Util.flat(vroot.CFrame.LookVector).Unit:Dot(dir) > 0.25
	hrp.CFrame = CFrame.lookAt(hrp.Position, hrp.Position + dir)
	local vpos = Vector3.new(hrp.Position.X, vroot.Position.Y, hrp.Position.Z) + dir * 3.1
	local face = stealth and dir or -dir
	vroot.CFrame = CFrame.lookAt(vpos, vpos + face)
	hrp.Anchored = true
	vroot.Anchored = true
	hum.AutoRotate = false

	S.Net.Anim(char, def.clip, "Action")
	S.Net.Anim(vm, def.victimClip or "Grabbed", "Action")
	S.Rig.SetExpression(vm, "Scared")
	S.Rig.SetExpression(char, "Psycho")
	if not stealth then
		S.Rig.Say(vm, Util.pick(Story.Lines.Scream), Config.UI.Warn, 2)
		S.NPC.Scream(vroot.Position, rec)
	end

	local hits = def.hits
	if Config.Gore().KillCam then
		task.delay(math.max(0, hits[#hits] - 0.45), function()
			if vm.Parent then
				S.Net.Screen(player, "KillCam", { attacker = char, victim = vm, duration = 1.35 })
			end
		end)
	end
	for i, t in hits do
		task.delay(t, function()
			if not vm.Parent then
				return
			end
			local final = i == #hits
			local pos = focusPoint(vm, def.focus) or vroot.Position
			S.Blood.FX("Hit", {
				pos = pos,
				dir = dir,
				power = def.blood * (final and 1.5 or 1),
				final = final,
				blunt = def.blunt == true,
				killer = player.UserId,
				attacker = char,
				victim = vm,
			})
			S.Net.Sfx(def.sound or "Stab", vroot, 0.85)
			S.Net.Sfx("Splat", vroot, 0.5 + 0.2 * def.blood)
			S.Net.Anim(vm, "Hit", "Hit")
			if def.kneelAfter and i == def.kneelAfter then
				S.Net.Anim(vm, "Kneel", "Base")
			end
			if i == 1 then
				local ut = vm:FindFirstChild("UpperTorso") :: BasePart?
				if ut then
					local wound = def.focus == "Neck" and CFrame.new(0, ut.Size.Y / 2, -0.3) or CFrame.new(0, 0.1, -ut.Size.Z / 2)
					S.Blood.Wound(ut, wound, def.blood, 10)
					if def.focus == "Neck" and Config.Gore().Arterial then
						S.Blood.FX("Arterial", { part = ut, offset = wound, dir = Vector3.new(0.2, 0.35, -1), duration = 4, power = 1.1 })
					end
				end
				S.Blood.Pool(vroot.Position, 2.4, 2.5, { victim = rec.def.name })
			end
			S.Blood.Stain(char, math.ceil(def.bloodiness / 9 / #hits) + 1)
			S.GameState.AddBloodiness(player, def.bloodiness / #hits)
			S.Net.Screen(player, final and "KillFlash" or "HitShake", { power = def.blood })
			if final then
				task.delay(0.25, function()
					if vm.Parent then
						S.Rig.Say(vm, Util.pick(Story.Lines.Dying), Color3.fromRGB(205, 175, 185), 2.2)
					end
				end)
			end
		end)
	end

	task.wait(def.duration)

	hrp.Anchored = false
	hum.AutoRotate = true
	S.NPC.Kill(rec, { weapon = def, dir = dir, killer = player })
	S.Blood.SetWeaponBloody(tool, true)
	st.Attacking = false
	st.Busy = false
	S.GameState.AddBPM(player, Config.BPM.PerKill)
	S.GameState.RefreshMood(player)
	task.delay(2.5, function()
		S.GameState.Thought(player, "Kill")
	end)
end

-- Fire axe on an existing body: chop it apart.
function Combat.DismemberBody(player: Player, body: Model)
	local st = S.GameState.Get(player)
	if not st or st.Busy or st.Dragging or st.Carrying then
		return
	end
	if not Config.Gore().Dismember then
		S.Net.Notify(player, "Dismemberment is off in this gore preset.", "info")
		return
	end
	local tool = S.Items.Equipped(player)
	if not tool or tool:GetAttribute("ItemId") ~= "FireAxe" then
		S.Net.Notify(player, "You'd need something heavier. An axe, maybe.", "info")
		return
	end
	local char = player.Character :: Model
	local hrp = char:FindFirstChild("HumanoidRootPart") :: BasePart
	local hum = char:FindFirstChildOfClass("Humanoid") :: Humanoid
	local torso = body:FindFirstChild("UpperTorso") :: BasePart?
	if not (hrp and hum and torso) then
		return
	end
	st.Busy = true
	st.Dismembering = true
	local dir = Util.flat(torso.Position - hrp.Position)
	dir = dir.Magnitude > 0.1 and dir.Unit or Util.flat(hrp.CFrame.LookVector).Unit
	hrp.CFrame = CFrame.lookAt(hrp.Position, hrp.Position + dir)
	hrp.Anchored = true
	hum.AutoRotate = false
	S.Net.Anim(char, "Chop", "Action")
	S.Rig.SetExpression(char, "Psycho")
	local order = { "Head", "RightArm", "LeftArm", "RightLeg", "LeftLeg" }
	for i, t in { 0.65, 1.15 } do
		task.delay(t, function()
			local hitPos = torso.Parent and torso.Position or hrp.Position + dir * 3
			S.Blood.FX("Hit", { pos = hitPos, dir = Vector3.new(0, -1, 0) + dir * 0.3, power = 1.6, final = i == 2, killer = player.UserId })
			S.Net.Sfx("Splat", hrp, 0.8, 0.85)
			S.Net.Sfx("Blunt", hrp, 0.7, 0.8)
			S.Net.Screen(player, i == 2 and "KillFlash" or "HitShake", { power = 1.4 })
			S.Blood.Stain(char, 3)
			S.GameState.AddBloodiness(player, 14)
			S.Blood.Pool(hitPos, 3, 1.5)
		end)
	end
	task.wait(1.45)
	for _, limb in order do
		S.Bodies.Sever(body, limb)
	end
	body:SetAttribute("Chopped", true)
	S.Bodies.RefreshPrompts(body)
	S.Blood.SetWeaponBloody(tool, true)
	hrp.Anchored = false
	hum.AutoRotate = true
	st.Dismembering = false
	st.Busy = false
	S.GameState.AddBPM(player, Config.BPM.PerDismember)
	S.GameState.RefreshMood(player)
end

return Combat

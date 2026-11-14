-- KUROBARA · Procedural animation clips
-- No uploaded animation assets needed: every clip is a list of keyframed Motor6D offsets
-- that the client layers on top of Roblox's default walk/idle (C0 = base * offset).
--
-- Axis cheat sheet (R15):
--   Shoulder +X  raise arm forward      Shoulder +Z  right arm out (left arm: -Z)
--   Elbow    +X  bend forearm up        Waist    +X  lean back, -X lean forward, +Y twist left
--   Neck     +X  look up, +Z tilt left  Hip      +X  thigh forward    Knee -X bend

local Anim = {}

local rad = math.rad

local function R(x: number?, y: number?, z: number?): CFrame
	return CFrame.fromEulerAnglesYXZ(rad(x or 0), rad(y or 0), rad(z or 0))
end

local function T(px: number, py: number, pz: number, x: number?, y: number?, z: number?): CFrame
	return CFrame.new(px, py, pz) * R(x, y, z)
end

Anim.R = R
Anim.T = T

Anim.JointNames = {
	Root = "Root", Waist = "Waist", Neck = "Neck",
	RS = "RightShoulder", RE = "RightElbow", RW = "RightWrist",
	LS = "LeftShoulder", LE = "LeftElbow", LW = "LeftWrist",
	RH = "RightHip", RK = "RightKnee", LH = "LeftHip", LK = "LeftKnee",
}

-- Layers are composed in this order (first = closest to the body).
Anim.LayerOrder = { "Base", "Mood", "Action", "Hit" }

-- Keys: { time, pose, ease }  ease ∈ "smooth" (default) | "in" (snappy strike) | "out" | "linear"
local C = {}

-- ── Locomotion / states (looping) ────────────────────────────────────────────
local crouchPose = {
	Root = T(0, -0.8, 0, -6), Waist = R(-20), Neck = R(14),
	RH = R(50), LH = R(50), RK = R(-75), LK = R(-75),
	RS = R(20, 0, 6), LS = R(20, 0, -6), RE = R(30), LE = R(30),
}
C.Crouch = { length = 2, loop = true, keys = {
	{ 0, crouchPose },
	{ 1, { Root = T(0, -0.86, 0, -7), Waist = R(-22), Neck = R(16), RH = R(52), LH = R(52), RK = R(-78), LK = R(-78), RS = R(22, 0, 6), LS = R(22, 0, -6), RE = R(32), LE = R(32) } },
	{ 2, crouchPose },
} }

local dragPose = { Root = T(0, -0.25, 0, -6), Waist = R(-22, -18), Neck = R(10, 10), RS = R(-40, 0, 28), RE = R(12), LS = R(10, 0, -6), LE = R(15) }
C.Drag = { length = 1.2, loop = true, keys = {
	{ 0, dragPose },
	{ 0.6, { Root = T(0, -0.32, 0, -8), Waist = R(-26, -16), Neck = R(6, 12), RS = R(-44, 0, 30), RE = R(14), LS = R(14, 0, -6), LE = R(18) } },
	{ 1.2, dragPose },
} }

local carryPose = { Waist = R(6), RS = R(58, -18), LS = R(58, 18), RE = R(58), LE = R(58) }
C.Carry = { length = 1, loop = true, keys = { { 0, carryPose }, { 0.5, { Waist = R(4), RS = R(60, -18), LS = R(60, 18), RE = R(55), LE = R(55) } }, { 1, carryPose } } }

C.Insane = { length = 3.4, loop = true, keys = {
	{ 0, { Neck = R(-6, 0, -16), Waist = R(-6), RS = R(4, 0, 3), LS = R(4, 0, -3) } },
	{ 1.4, { Neck = R(-10, 8, -24), Waist = R(-8), RS = R(6, 0, 4), LS = R(6, 0, -4) } },
	{ 1.48, { Neck = R(-2, -10, 12), Waist = R(-4, 4), RS = R(2), LS = R(2) }, "in" },
	{ 1.9, { Neck = R(-8, 0, -18), Waist = R(-6) } },
	{ 3.4, { Neck = R(-6, 0, -16), Waist = R(-6), RS = R(4, 0, 3), LS = R(4, 0, -3) } },
} }

C.Panic = { length = 0.5, loop = true, keys = {
	{ 0, { RS = R(160, 0, 25), LS = R(160, 0, -25), RE = R(20), LE = R(20), Neck = R(14) } },
	{ 0.25, { RS = R(148, 0, 38), LS = R(148, 0, -38), RE = R(35), LE = R(35), Neck = R(18) } },
	{ 0.5, { RS = R(160, 0, 25), LS = R(160, 0, -25), RE = R(20), LE = R(20), Neck = R(14) } },
} }

local cower = { Root = T(0, -1.25, 0), RH = R(-5), LH = R(-5), RK = R(-95), LK = R(-95), Waist = R(-45), Neck = R(-30), RS = R(150, -30), LS = R(150, 30), RE = R(120), LE = R(120) }
C.Cower = { length = 0.4, loop = true, keys = {
	{ 0, cower },
	{ 0.2, { Root = T(0.05, -1.25, 0), RH = R(-5), LH = R(-5), RK = R(-95), LK = R(-95), Waist = R(-43, 3), Neck = R(-28), RS = R(152, -30), LS = R(152, 30), RE = R(118), LE = R(118) } },
	{ 0.4, cower },
} }

local mop = { RS = R(55, 40), LS = R(60, 30), RE = R(30), LE = R(40), Waist = R(-15, 25) }
C.Mop = { length = 1, loop = true, keys = { { 0, mop }, { 0.5, { RS = R(55, -40), LS = R(60, -30), RE = R(30), LE = R(40), Waist = R(-15, -25) } }, { 1, mop } } }

local dig = { RS = R(110), LS = R(110), RE = R(30), LE = R(30), Waist = R(5) }
C.Dig = { length = 1.1, loop = true, keys = {
	{ 0, dig },
	{ 0.45, { RS = R(35), LS = R(35), Waist = R(-40), Root = T(0, -0.5, 0) }, "in" },
	{ 0.8, { RS = R(60), LS = R(60), Waist = R(-20, 30), RE = R(10), LE = R(10) } },
	{ 1.1, dig },
} }

local wash = { RS = R(55, 10), LS = R(55, -10), RE = R(60), LE = R(60), Waist = R(-12) }
C.Wash = { length = 0.6, loop = true, keys = { { 0, wash }, { 0.3, { RS = R(60, -10), LS = R(60, 10), RE = R(64), LE = R(64), Waist = R(-12) } }, { 0.6, wash } } }

local shower = { RS = R(165, -30), RE = R(100), LS = R(165, 30), LE = R(100), Neck = R(12) }
C.Shower = { length = 0.8, loop = true, keys = { { 0, shower }, { 0.4, { RS = R(158, -40), RE = R(110), LS = R(158, 40), LE = R(110), Neck = R(16) } }, { 0.8, shower } } }

C.Phone = { length = 0.3, hold = true, keys = { { 0, {} }, { 0.3, { RS = R(95, -25), RE = R(70), Neck = R(-4) } } } }
C.Point = { length = 0.25, hold = true, keys = { { 0, {} }, { 0.25, { RS = R(92), RE = R(0), Waist = R(4) } } } }
C.Arrest = { length = 0.3, hold = true, keys = { { 0, {} }, { 0.3, { RS = R(95, -10), LS = R(95, 10), RE = R(10), LE = R(10), Waist = R(-10) } } } }

-- ── Social ───────────────────────────────────────────────────────────────────
C.Talk = { length = 1.4, keys = {
	{ 0, {} },
	{ 0.3, { RS = R(35, 0, 15), RE = R(55), Neck = R(4, 0, 4) } },
	{ 0.7, { RS = R(28, -10, 10), RE = R(70), Neck = R(-2, 0, -3) } },
	{ 1.0, { RS = R(35, 0, 15), RE = R(50) } },
	{ 1.4, {} },
} }
C.Wave = { length = 1.4, keys = {
	{ 0, {} },
	{ 0.25, { RS = R(0, 0, 150), RE = R(35) } },
	{ 0.5, { RS = R(0, 0, 135), RE = R(55) } },
	{ 0.75, { RS = R(0, 0, 150), RE = R(35) } },
	{ 1.0, { RS = R(0, 0, 135), RE = R(55) } },
	{ 1.4, {} },
} }
C.Scream = { length = 1.4, keys = {
	{ 0, {} },
	{ 0.12, { RS = R(150, -40), LS = R(150, 40), RE = R(120), LE = R(120), Neck = R(25), Waist = R(12) }, "in" },
	{ 1.0, { RS = R(152, -42), LS = R(152, 42), RE = R(122), LE = R(122), Neck = R(28), Waist = R(14) } },
	{ 1.4, {} },
} }
C.Laugh = { length = 1.6, keys = {
	{ 0, {} },
	{ 0.15, { Neck = R(30), Waist = R(14), RS = R(10, 0, 20), LS = R(10, 0, -20) } },
	{ 0.3, { Neck = R(24), Waist = R(6) } },
	{ 0.45, { Neck = R(32), Waist = R(16), RS = R(12, 0, 22), LS = R(12, 0, -22) } },
	{ 0.6, { Neck = R(24), Waist = R(6) } },
	{ 0.75, { Neck = R(34), Waist = R(18), RS = R(12, 0, 24), LS = R(12, 0, -24) } },
	{ 1.2, { Neck = R(-20, 0, -18), Waist = R(-12) } },
	{ 1.6, {} },
} }

-- ── Idle life ────────────────────────────────────────────────────────────────
C.LookAround = { length = 2.6, keys = {
	{ 0, {} },
	{ 0.5, { Neck = R(4, 42), Waist = R(0, 10) } },
	{ 1.1, { Neck = R(4, 40), Waist = R(0, 10) } },
	{ 1.6, { Neck = R(-2, -40), Waist = R(0, -10) } },
	{ 2.1, { Neck = R(-2, -38), Waist = R(0, -9) } },
	{ 2.6, {} },
} }
C.ArmsCrossed = { length = 0.45, hold = true, keys = { { 0, {} }, { 0.45, { RS = R(62, 52), RE = R(98), LS = R(58, -52), LE = R(102), Waist = R(4), Neck = R(4, 0, 4) } } } }
C.HandsBehind = { length = 0.45, hold = true, keys = { { 0, {} }, { 0.45, { RS = R(-22, -20, 6), RE = R(45, 30), LS = R(-22, 20, -6), LE = R(45, -30), Waist = R(5), Neck = R(6) } } } }
C.PhoneIdle = { length = 0.4, hold = true, keys = { { 0, {} }, { 0.4, { RS = R(72, -18), RE = R(85), LS = R(55, 20), LE = R(70), Neck = R(-20) } } } }
C.Stretch = { length = 2.2, keys = {
	{ 0, {} },
	{ 0.6, { RS = R(172, 0, 12), LS = R(172, 0, -12), RE = R(10), LE = R(10), Waist = R(12), Neck = R(18) } },
	{ 1.4, { RS = R(176, 0, 18), LS = R(176, 0, -18), RE = R(5), LE = R(5), Waist = R(16, 0, 4), Neck = R(22) } },
	{ 2.2, {} },
} }
C.Yawn = { length = 2.2, keys = {
	{ 0, {} },
	{ 0.5, { RS = R(140, -35), RE = R(125), Neck = R(20), Waist = R(8) } },
	{ 1.5, { RS = R(142, -36), RE = R(128), Neck = R(24), Waist = R(10) } },
	{ 2.2, {} },
} }
C.Write = { length = 1.4, keys = {
	{ 0, {} },
	{ 0.3, { RS = R(52, 18), RE = R(62), LS = R(40, -20), LE = R(70), Neck = R(-22) } },
	{ 0.6, { RS = R(54, 10), RE = R(58), LS = R(40, -20), LE = R(70), Neck = R(-22) } },
	{ 0.9, { RS = R(52, 20), RE = R(64), LS = R(40, -20), LE = R(70), Neck = R(-24) } },
	{ 1.4, {} },
} }
-- Nyx only
C.HairTwirl = { length = 2.8, keys = {
	{ 0, {} },
	{ 0.5, { RS = R(150, -42, 12), RE = R(128), Neck = R(-4, 0, 12), Waist = R(0, 0, 3) } },
	{ 0.9, { RS = R(152, -40, 12), RE = R(138), Neck = R(-4, 0, 14) } },
	{ 1.3, { RS = R(150, -42, 12), RE = R(124), Neck = R(-4, 0, 12) } },
	{ 1.7, { RS = R(152, -40, 12), RE = R(138), Neck = R(-4, 0, 14) } },
	{ 2.2, { RS = R(150, -42, 12), RE = R(126), Neck = R(-4, 0, 12) } },
	{ 2.8, {} },
} }
C.HugSelf = { length = 3.2, keys = {
	{ 0, {} },
	{ 0.6, { RS = R(55, 62), RE = R(112), LS = R(55, -62), LE = R(112), Waist = R(-10), Neck = R(-14, 0, 8) } },
	{ 2.4, { RS = R(57, 64), RE = R(114), LS = R(57, -64), LE = R(114), Waist = R(-12, 0, 2), Neck = R(-16, 0, 10) } },
	{ 3.2, {} },
} }
C.Sway = { length = 3.2, keys = {
	{ 0, {} },
	{ 0.8, { Root = T(0, 0, 0, 0, 0, 3), Neck = R(0, 0, -9), RS = R(6, 0, 4), LS = R(6, 0, -4) } },
	{ 1.6, { Root = T(0, 0, 0, 0, 0, -3), Neck = R(0, 0, 9), RS = R(6, 0, 4), LS = R(6, 0, -4) } },
	{ 2.4, { Root = T(0, 0, 0, 0, 0, 3), Neck = R(0, 0, -9) } },
	{ 3.2, {} },
} }

-- ── Attacks (Action layer). Hit times must match Items.Weapons[...].hits ─────
local kRaise = { LS = R(85, -15), LE = R(10), RS = R(152, 0, 16), RE = R(35), Waist = R(-5, 14), Neck = R(-6) }
local kStab = { LS = R(80, -15), LE = R(10), RS = R(55, 0, 4), RE = R(4), Waist = R(-20, -10), Neck = R(-14) }
C.Knife = { length = 1.95, keys = {
	{ 0, {} },
	{ 0.14, kRaise },
	{ 0.33, kStab, "in" },
	{ 0.47, kRaise, "out" },
	{ 0.6, kStab, "in" },
	{ 0.72, kRaise, "out" },
	{ 0.85, kStab, "in" },
	{ 1.05, { LS = R(60, -10), RS = R(178, 0, 10), RE = R(25), Waist = R(8, 20), Neck = R(10) }, "out" },
	{ 1.25, { LS = R(40), RS = R(40), RE = R(0), Waist = R(-32, -15), Neck = R(-20), Root = T(0, -0.35, 0, -6) }, "in" },
	{ 1.6, { LS = R(36), RS = R(38), Waist = R(-30, -14), Neck = R(-24, 0, -8), Root = T(0, -0.35, 0, -6) } },
	{ 1.95, {} },
} }

C.BoxCutter = { length = 1.4, keys = {
	{ 0, {} },
	{ 0.16, { LS = R(80, -20), RS = R(95, 65), RE = R(25), Waist = R(0, 25) } },
	{ 0.3, { LS = R(80, -20), RS = R(88, -72), RE = R(0), Waist = R(-8, -25) }, "in" },
	{ 0.55, { LS = R(70, -20), RS = R(100, 55), RE = R(30), Waist = R(-4, 18) }, "out" },
	{ 0.7, { LS = R(70, -20), RS = R(84, -66), RE = R(0), Waist = R(-10, -22) }, "in" },
	{ 1.0, { LS = R(30), RS = R(70, -50), Waist = R(-8, -18), Neck = R(-10, 0, -10) } },
	{ 1.4, {} },
} }

local batOver = { Waist = R(14), RS = R(172), LS = R(172), RE = R(15), LE = R(15), Neck = R(15) }
C.Bat = { length = 1.8, keys = {
	{ 0, {} },
	{ 0.42, { Waist = R(0, -65), RS = R(110, -35, 20), LS = R(115, -30), RE = R(40), LE = R(50), Neck = R(0, 40) } },
	{ 0.56, { Waist = R(-8, 55), RS = R(90, 70), LS = R(95, 70), RE = R(5), LE = R(5), Neck = R(0, -20) }, "in" },
	{ 0.95, batOver, "out" },
	{ 1.2, { Waist = R(-38), RS = R(35), LS = R(35), Neck = R(-25), Root = T(0, -0.4, 0, -8) }, "in" },
	{ 1.45, { Waist = R(-36), RS = R(32), LS = R(32), Neck = R(-28), Root = T(0, -0.4, 0, -8) } },
	{ 1.8, {} },
} }

local axeOver = { Waist = R(15), RS = R(176, 0, 6), LS = R(176, 0, -6), RE = R(20), LE = R(20), Neck = R(18) }
C.Axe = { length = 2.0, keys = {
	{ 0, {} },
	{ 0.48, axeOver },
	{ 0.68, { Waist = R(-36), RS = R(40), LS = R(40), Neck = R(-22), Root = T(0, -0.4, 0, -6) }, "in" },
	{ 1.05, axeOver, "out" },
	{ 1.33, { Waist = R(-48), RS = R(18), LS = R(18), Neck = R(-30), Root = T(0, -0.9, 0, -10), RH = R(30), LH = R(30), RK = R(-45), LK = R(-45) }, "in" },
	{ 1.6, { Waist = R(-46), RS = R(16), LS = R(16), Neck = R(-32, 0, 10), Root = T(0, -0.9, 0, -10), RH = R(30), LH = R(30), RK = R(-45), LK = R(-45) } },
	{ 2.0, {} },
} }

local shBack = { RS = R(70, 0, 8), LS = R(70, 0, -8), RE = R(80), LE = R(80), Waist = R(4) }
local shThrust = { RS = R(92), LS = R(92), RE = R(0), LE = R(0), Waist = R(-16) }
C.Shears = { length = 1.25, keys = {
	{ 0, {} },
	{ 0.2, shBack },
	{ 0.35, shThrust, "in" },
	{ 0.55, shBack, "out" },
	{ 0.7, shThrust, "in" },
	{ 0.95, { RS = R(88), LS = R(88), Waist = R(-18), Neck = R(-12) } },
	{ 1.25, {} },
} }

C.Chop = { length = 1.45, keys = {
	{ 0, {} },
	{ 0.42, axeOver },
	{ 0.65, { Waist = R(-55), RS = R(10), LS = R(10), Neck = R(-35), Root = T(0, -1.1, 0, -10), RH = R(40), LH = R(40), RK = R(-60), LK = R(-60) }, "in" },
	{ 0.95, axeOver, "out" },
	{ 1.15, { Waist = R(-55), RS = R(10), LS = R(10), Neck = R(-35), Root = T(0, -1.1, 0, -10), RH = R(40), LH = R(40), RK = R(-60), LK = R(-60) }, "in" },
	{ 1.45, {} },
} }

C.Swing = { length = 0.6, keys = {
	{ 0, {} },
	{ 0.15, { RS = R(140, 0, 10), RE = R(30), Waist = R(4, 15) } },
	{ 0.3, { RS = R(40), RE = R(0), Waist = R(-14, -12) }, "in" },
	{ 0.6, {} },
} }

-- ── Victim reactions ─────────────────────────────────────────────────────────
C.Grabbed = { length = 0.25, hold = true, keys = { { 0, {} }, { 0.25, { Waist = R(10), Neck = R(15), RS = R(70, 0, 25), LS = R(70, 0, -25), RE = R(60), LE = R(60) }, "out" } } }
C.Clutch = { length = 0.3, hold = true, keys = { { 0, {} }, { 0.3, { RS = R(150, -40), RE = R(110), LS = R(150, 40), LE = R(110), Neck = R(-10), Waist = R(-10) }, "out" } } }
C.Kneel = { length = 0.35, hold = true, keys = { { 0, {} }, { 0.35, { Root = T(0, -1.35, 0), RH = R(-5), LH = R(-5), RK = R(-95), LK = R(-95), Waist = R(-20), Neck = R(-15), RS = R(15, 0, 8), LS = R(15, 0, -8) }, "in" } } }
C.Hit = { length = 0.24, keys = {
	{ 0, {} },
	{ 0.05, { Waist = R(14, 0, 4), Neck = R(22, 0, 8), Root = T(0, 0, 0.25) }, "in" },
	{ 0.24, {} },
} }

Anim.Clips = C

-- ── Evaluation ───────────────────────────────────────────────────────────────
local IDENT = CFrame.identity

local function ease(kind: string?, a: number): number
	if kind == "linear" then
		return a
	elseif kind == "in" then
		return a * a * a
	elseif kind == "out" then
		return 1 - (1 - a) ^ 3
	end
	return a * a * (3 - 2 * a)
end

-- Returns a map jointKey -> CFrame offset for clip at time t.
function Anim.Sample(clip, t: number)
	local keys = clip.keys
	local n = #keys
	if clip.loop then
		t = t % clip.length
	end
	if t <= keys[1][1] then
		return keys[1][2]
	end
	if t >= keys[n][1] then
		return keys[n][2]
	end
	for i = 1, n - 1 do
		local a, b = keys[i], keys[i + 1]
		if t >= a[1] and t <= b[1] then
			local span = math.max(b[1] - a[1], 1e-4)
			local alpha = ease(b[3], (t - a[1]) / span)
			local out = {}
			for j, cf in a[2] do
				out[j] = cf:Lerp(b[2][j] or IDENT, alpha)
			end
			for j, cf in b[2] do
				if out[j] == nil then
					out[j] = IDENT:Lerp(cf, alpha)
				end
			end
			return out
		end
	end
	return keys[n][2]
end

return Anim

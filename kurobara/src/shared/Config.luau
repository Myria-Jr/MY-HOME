-- KUROBARA · Config
-- Every tunable number lives here. Change things, press Play, feel the difference.

local Config = {}

Config.Title = "KUROBARA"
Config.Tagline = "five days. one heartbeat."

--[[ GORE PRESET ───────────────────────────────────────────────────────────────
	"Extreme"      Everything on: dismemberment, arterial spray, screen blood.
	               Fine for Studio / private testing.
	"Restricted17" Heavy realistic blood, NO dismemberment.
	               Use this if you publish with a 17+ (Restricted) maturity label.
	"Mild"         Stylized, light blood. Use this for anything below 17+.

	⚠ Roblox moderates gore hard. Publishing "Extreme" can get the game taken
	  down or your account warned. Switch the preset BEFORE you publish.
──────────────────────────────────────────────────────────────────────────── ]]
Config.GorePreset = "Extreme"

Config.GorePresets = {
	Extreme = {
		Droplets = 1.0, Dismember = true, Arterial = true, ScreenBlood = true,
		PoolScale = 1.0, MaxSplats = 1100, WallDrips = true,
		Jets = true, Clots = true, Footprints = true, KillCam = true, Hitstop = true,
	},
	Restricted17 = {
		Droplets = 0.75, Dismember = false, Arterial = true, ScreenBlood = true,
		PoolScale = 0.9, MaxSplats = 700, WallDrips = true,
		Jets = true, Clots = false, Footprints = true, KillCam = true, Hitstop = true,
	},
	Mild = {
		Droplets = 0.25, Dismember = false, Arterial = false, ScreenBlood = false,
		PoolScale = 0.5, MaxSplats = 120, WallDrips = false,
		Jets = false, Clots = false, Footprints = false, KillCam = true, Hitstop = true,
	},
}

function Config.Gore()
	return Config.GorePresets[Config.GorePreset] or Config.GorePresets.Mild
end

-- Blood palette
Config.Blood = {
	Fresh = Color3.fromRGB(148, 8, 18),
	Base = Color3.fromRGB(112, 4, 12),
	Dark = Color3.fromRGB(58, 2, 6),
	Dried = Color3.fromRGB(70, 18, 16),
}

-- ── Time ─────────────────────────────────────────────────────────────────────
Config.Time = {
	MinutesPerSecond = 0.8, -- in-game minutes per real second (0.8 → a school day ≈ 14 min)
	DayStart = 7 * 60,
	DayEnd = 18 * 60,
}

Config.Periods = {
	{ id = "Morning", start = 7 * 60, label = "Before Class" },
	{ id = "Class1", start = 8 * 60 + 30, label = "Class", class = true },
	{ id = "Lunch", start = 12 * 60, label = "Lunch Break" },
	{ id = "Class2", start = 13 * 60 + 30, label = "Class", class = true },
	{ id = "AfterSchool", start = 15 * 60 + 30, label = "After School" },
	{ id = "Leaving", start = 17 * 60 + 30, label = "Going Home" },
}

Config.Days = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday" }
Config.ConfessionDay = 5
Config.ConfessionTime = 17 * 60

-- ── Player ───────────────────────────────────────────────────────────────────
Config.Player = {
	WalkSpeed = 11,
	RunSpeed = 19,
	CrouchSpeed = 6,
	DragSpeed = 7,
	CarrySpeed = 9,
}

-- Heartbeat = sanity. Higher BPM → twitchier Nyx, scarier screen, more suspicious NPCs.
Config.BPM = {
	Base = 72,
	Elevated = 110,
	Unstable = 140,
	Breakdown = 175,
	Max = 200,
	PerKill = 26,
	PerDismember = 18,
	DecayPerMinute = 0.35, -- per in-game minute, everywhere
	HaruCalmPerMinute = 3.5, -- per in-game minute while near Haru
	DiaryReset = true,
}

Config.Reputation = { Min = -100, Max = 100 }

-- ── NPC perception ───────────────────────────────────────────────────────────
Config.Vision = {
	Range = 50,
	FOV = 130,
	CreepyRange = 16, -- how close NPCs notice an unstable heartbeat
	WeaponRange = 26, -- how far a held (clean) weapon is noticed
	PoolMinSize = 2.2, -- blood pools smaller than this are ignored
	TickRate = 0.3,
}

Config.NPC = {
	WalkSpeed = 9,
	FleeSpeed = 17,
	ChaseSpeed = 17.5,
	HeroChaseSpeed = 18.5,
	CatchDistance = 3.6,
	ScreamRadius = 34,
	ExtraStudents = 24, -- background students (lower this if the server struggles)
	WanderEvery = { 35, 80 }, -- seconds between little walks
}

Config.Detective = {
	FollowDistance = 15,
	WatchPerSecond = 0.55, -- suspicion while he watches you after someone went missing
	Minor = 8, -- weapon / creepy
	Major = 30, -- blood on you
	NightDecay = 25,
}

Config.Police = {
	ArrivalSeconds = 150,
}

Config.Kill = {
	Range = 6,
	ConeDegrees = 80,
}

-- ── Audio & voices ───────────────────────────────────────────────────────────
-- Sound IDs go in Shared/AudioIds. Voices use Roblox text-to-speech (no upload needed).
-- TTS voice IDs: 1 British m · 2 British f · 3 US m · 4 US f · 5 US m2 · 6 US f2 · 7 Aus m · 8 Aus f
Config.Audio = {
	SfxVolume = 1.0,
	MusicVolume = 0.32,
}

Config.Voice = {
	Enabled = true,
	Thoughts = true, -- Nyx's whispered inner voice
	MaxConcurrent = 3,
	MaxDistance = 60,
	FemaleVoices = { "2", "4", "6", "8" },
	MaleVoices = { "1", "3", "5", "7" },
	Nyx = { id = "2", pitch = -1, speed = 0.94 },
	PA = { id = "6", pitch = 0, speed = 1.0 },
	-- per-character overrides (by NPC id)
	Cast = {
		Haru = { id = "1", pitch = -1, speed = 0.95 },
		Mirei = { id = "6", pitch = 3, speed = 1.1 },
		Ryo = { id = "5", pitch = 1, speed = 1.16 },
		Kuroda = { id = "7", pitch = -4, speed = 0.92 },
	},
}

-- ── UI ───────────────────────────────────────────────────────────────────────
Config.UI = {
	Font = "rbxasset://fonts/families/BuilderSans.json", -- fallback: "rbxasset://fonts/families/GothamSSm.json"
	Accent = Color3.fromRGB(255, 59, 92),
	Love = Color3.fromRGB(255, 120, 170),
	Warn = Color3.fromRGB(255, 69, 58),
	Calm = Color3.fromRGB(120, 220, 255),
	Ink = Color3.fromRGB(245, 245, 250),
	SubInk = Color3.fromRGB(190, 190, 205),
}

Config.Debug = false

return Config

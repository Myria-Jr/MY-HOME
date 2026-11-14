-- KUROBARA · Story, cast & dialogue (all original)

local Story = {}

Story.Protagonist = {
	Name = "Nyx Amane",
	Short = "Nyx",
	Look = {
		gender = "F", hair = "SideLong", hairColor = Color3.fromRGB(18, 16, 24),
		hairTip = Color3.fromRGB(150, 10, 40), eyeColor = Color3.fromRGB(220, 20, 60),
		skin = Color3.fromRGB(255, 226, 214), socks = Color3.fromRGB(20, 20, 26), height = 1.0,
	},
}

Story.Intro = {
	"Nyx Amane has never heard her own heartbeat.",
	"Not at funerals. Not at fireworks. Not once.",
	"Then, on the first day of spring term, Haru Seto held a door open for her —",
	"and it started. Loud. Fast. Hers.",
	"Now there's a rumor: Mirei Hanazono will confess to him on Friday, at sunset, on the rooftop.",
	"Five days.",
	"Nyx isn't letting anyone steal the only sound she has ever heard.",
}

-- persona: "Coward" flees & reports · "Hero" chases you · "Phone" films you, then runs
-- class:   "2A" (Nyx's class) · "3A" (Haru & Mirei) · "1B"
-- morning/lunch/club = spot groups built by the map
Story.Students = {
	-- Class 2-A
	{ id = "Aoi", name = "Aoi Kanzaki", gender = "F", hair = "Bob", hairColor = Color3.fromRGB(38, 46, 92), eyeColor = Color3.fromRGB(70, 110, 220), persona = "Coward", class = "2A", morning = "Courtyard", lunch = "Cafeteria", club = "Art" },
	{ id = "Ren", name = "Ren Takeda", gender = "M", hair = "Spiky", hairColor = Color3.fromRGB(25, 22, 22), eyeColor = Color3.fromRGB(120, 80, 40), persona = "Hero", class = "2A", morning = "Field", lunch = "Cafeteria", club = "Field" },
	{ id = "Sora", name = "Sora Imai", gender = "F", hair = "Ponytail", hairColor = Color3.fromRGB(120, 70, 40), eyeColor = Color3.fromRGB(150, 100, 50), persona = "Phone", class = "2A", morning = "Lobby", lunch = "Roof", club = "Music" },
	{ id = "Kenta", name = "Kenta Morishita", gender = "M", hair = "Short", hairColor = Color3.fromRGB(80, 55, 35), eyeColor = Color3.fromRGB(90, 60, 40), persona = "Coward", class = "2A", morning = "Courtyard", lunch = "Cafeteria", club = "Science" },
	{ id = "Hina", name = "Hina Asakura", gender = "F", hair = "Long", hairColor = Color3.fromRGB(230, 190, 120), eyeColor = Color3.fromRGB(80, 150, 90), persona = "Coward", class = "2A", morning = "Fountain", lunch = "Courtyard", club = "Cafeteria" },
	{ id = "Daichi", name = "Daichi Ono", gender = "M", hair = "Short", hairColor = Color3.fromRGB(35, 30, 30), eyeColor = Color3.fromRGB(70, 50, 40), persona = "Hero", class = "2A", morning = "Gate", lunch = "Courtyard", club = "Gym" },
	-- Class 3-A
	{ id = "Yui", name = "Yui Nakagawa", gender = "F", hair = "Bun", hairColor = Color3.fromRGB(150, 60, 35), eyeColor = Color3.fromRGB(110, 70, 40), persona = "Coward", class = "3A", morning = "Courtyard", lunch = "Roof", club = "Garden" },
	{ id = "Takumi", name = "Takumi Fujiwara", gender = "M", hair = "Spiky", hairColor = Color3.fromRGB(150, 150, 160), eyeColor = Color3.fromRGB(60, 90, 140), persona = "Hero", class = "3A", morning = "Field", lunch = "Roof", club = "Field" },
	{ id = "Emi", name = "Emi Kojima", gender = "F", hair = "Twintails", hairColor = Color3.fromRGB(170, 140, 220), eyeColor = Color3.fromRGB(150, 90, 200), persona = "Phone", class = "3A", morning = "Fountain", lunch = "Cafeteria", club = "Art" },
	{ id = "Shun", name = "Shun Arakawa", gender = "M", hair = "Short", hairColor = Color3.fromRGB(20, 20, 24), eyeColor = Color3.fromRGB(50, 50, 60), persona = "Coward", class = "3A", morning = "Lobby", lunch = "Cafeteria", club = "Library" },
	-- Class 1-B
	{ id = "Mio", name = "Mio Sakurai", gender = "F", hair = "Ponytail", hairColor = Color3.fromRGB(190, 110, 120), eyeColor = Color3.fromRGB(200, 90, 120), persona = "Coward", class = "1B", morning = "Courtyard", lunch = "Courtyard", club = "Music" },
	{ id = "Kaito", name = "Kaito Yamada", gender = "M", hair = "Spiky", hairColor = Color3.fromRGB(120, 45, 30), eyeColor = Color3.fromRGB(140, 60, 30), persona = "Hero", class = "1B", morning = "Gate", lunch = "Cafeteria", club = "Gym" },
	{ id = "Rina", name = "Rina Tsukishima", gender = "F", hair = "Long", hairColor = Color3.fromRGB(200, 205, 220), eyeColor = Color3.fromRGB(90, 160, 200), persona = "Phone", class = "1B", morning = "Lobby", lunch = "Roof", club = "Science" },
	{ id = "Yuto", name = "Yuto Ishikawa", gender = "M", hair = "Short", hairColor = Color3.fromRGB(95, 65, 40), eyeColor = Color3.fromRGB(90, 70, 50), persona = "Coward", class = "1B", morning = "Courtyard", lunch = "Cafeteria", club = "Library" },
	{ id = "Nanami", name = "Nanami Kuroe", gender = "F", hair = "Bob", hairColor = Color3.fromRGB(20, 60, 60), eyeColor = Color3.fromRGB(40, 160, 150), persona = "Coward", class = "1B", morning = "Fountain", lunch = "Courtyard", club = "Garden" },
	{ id = "Riku", name = "Riku Hayama", gender = "M", hair = "Short", hairColor = Color3.fromRGB(55, 40, 30), eyeColor = Color3.fromRGB(80, 60, 45), persona = "Coward", class = "1B", morning = "Gate", lunch = "Cafeteria", club = "Cafeteria" },
}

Story.Haru = {
	id = "Haru", name = "Haru Seto", gender = "M", hair = "Messy", hairColor = Color3.fromRGB(58, 40, 32),
	eyeColor = Color3.fromRGB(120, 125, 140), glasses = true, persona = "Crush", class = "3A",
	morning = "FountainBench", lunch = "Roof", club = "Library",
}

Story.Mirei = {
	id = "Mirei", name = "Mirei Hanazono", gender = "F", hair = "Twintails", hairColor = Color3.fromRGB(255, 140, 170),
	eyeColor = Color3.fromRGB(60, 180, 110), persona = "Rival", class = "3A",
	morning = "Greenhouse", lunch = "Roof", club = "Garden",
}

Story.Teachers = {
	{ id = "Tachibana", name = "Ms. Tachibana", gender = "F", hair = "Bun", hairColor = Color3.fromRGB(30, 26, 30), eyeColor = Color3.fromRGB(60, 50, 50), persona = "Teacher", class = "2A", height = 1.08 },
	{ id = "Okada", name = "Mr. Okada", gender = "M", hair = "Short", hairColor = Color3.fromRGB(130, 130, 135), eyeColor = Color3.fromRGB(60, 60, 70), persona = "Teacher", class = "3A", height = 1.1, glasses = true },
	{ id = "Fujimura", name = "Ms. Fujimura", gender = "F", hair = "Long", hairColor = Color3.fromRGB(90, 55, 35), eyeColor = Color3.fromRGB(100, 70, 50), persona = "Teacher", class = "1B", height = 1.06 },
	{ id = "Kuroda", name = "Mr. Kuroda", gender = "M", hair = "Spiky", hairColor = Color3.fromRGB(15, 15, 18), eyeColor = Color3.fromRGB(40, 40, 45), persona = "Teacher", class = "Hall", height = 1.14, patrol = true },
}

Story.Lines = {
	Talk = {
		"Did you finish the math homework? …Me neither.",
		"The festival's on Friday! Are you going?",
		"I heard the roses in the courtyard bloom black at night.",
		"The cafeteria curry is a war crime.",
		"Have you seen Haru-senpai reading by the fountain? So dreamy.",
		"Mirei's been acting weird. Like… nervous-happy weird.",
		"Don't go near the incinerator after school. It smells.",
		"I swear the clock in 2-A runs slow.",
		"You're new, right? Nyx? Cool name.",
		"Mr. Kuroda gave me detention for walking too loudly.",
	},
	Haru = {
		"Oh — hey. You're in 2-A, right?",
		"This book is better than it looks. The cover lies.",
		"The rooftop is the only quiet place in this school.",
		"You always look like you're listening to something.",
		"Mirei keeps asking me if I'm free on Friday. Weird, right?",
		"…Nyx. I'll remember that.",
	},
	Mirei = {
		"Friday! I'm finally going to say it. Wish me luck!",
		"The greenhouse is my favorite place. Nobody comes here.",
		"Haru likes white lilies. I'm growing some just for him.",
		"Do you think sunset is romantic? I think sunset is romantic.",
		"You have pretty eyes. Kinda red. Kinda scary. Haha.",
	},
	Teacher = {
		"Walk, don't run.",
		"Shouldn't you be studying?",
		"Uniform rules apply after school too, Amane.",
		"If you see anything strange, report it to me.",
	},
	Scream = { "AAAAAAH!!", "SOMEONE HELP!!", "OH MY GOD—", "NO NO NO NO", "MURDER!!", "S-STAY AWAY!!" },
	Corpse = { "Is— is that a body?!", "SHE'S NOT MOVING!", "Call the police!!", "Blood… so much blood…" },
	Blood = { "Is that… blood?", "Why is the floor red?", "Something's wrong here…" },
	Bloody = { "Why are you covered in blood?!", "Nyx…? What happened to you?", "D-don't come closer!" },
	Weapon = { "Why are you carrying that?", "Uh… is that a weapon?", "That's creepy, Nyx." },
	Creepy = { "Why is she smiling like that?", "Nyx is giving me the creeps.", "Are you… okay?" },
	Phone = { "*click* Got you.", "I'm recording this!", "Smile for the police." },
	Hero = { "STOP RIGHT THERE!", "You're not getting away!", "I've got you!" },
	TeacherChase = { "AMANE! STOP!", "Don't move!", "Somebody call the police!" },
	Haunted = { "Where is everyone…?", "Has anyone seen my friend?" },
}

Story.Objectives = {
	"Get rid of Mirei before Friday sunset.",
	"Mirei is still here. Four days left.",
	"Halfway there. Mirei is getting bolder.",
	"Two days. Mirei bought a new ribbon.",
	"TODAY. Before 17:00 — or she confesses on the rooftop.",
}

Story.GameOver = {
	Apprehended = { title = "APPREHENDED", text = "A teacher caught you. They don't let girls like you go home." },
	Overpowered = { title = "OVERPOWERED", text = "A student pinned you to the floor until the sirens came." },
	Arrested = { title = "ARRESTED", text = "The police found what they needed." },
	Heartbreak = { title = "HEARTBREAK", text = "Haru saw everything. He'll never look at you the same way again." },
	Expelled = { title = "EXPELLED", text = "Your reputation hit rock bottom. The school asked you to leave." },
}

Story.Endings = {
	Good = {
		title = "HEARTBEAT",
		lines = {
			"Friday. Sunset. The rooftop.",
			"Haru is there — alone. Waiting for nobody.",
			"He says your name like he's known it forever.",
			"Somewhere below, the roses have never looked so red.",
		},
	},
	Neutral = {
		title = "STILL BEATING",
		lines = {
			"Mirei never came back. The rumor died with her.",
			"Haru still doesn't really know your name.",
			"But your heart is still loud. Still fast. Still yours.",
			"And there's always next week.",
		},
	},
	Bad = {
		title = "SILENCE",
		lines = {
			"At sunset, Mirei climbed the stairs to the rooftop.",
			"Three floors below, you heard the word \"yes\".",
			"Then nothing.",
			"Your heart never made another sound.",
		},
	},
}

Story.Notes = {
	{
		title = "Mirei Hanazono · 3-A",
		body = "07:00 – 07:45  Greenhouse (alone)\n07:45 – 08:30  Courtyard with friends\nLunch          Rooftop with Haru & Yui\n15:30 – 17:00  Garden club\n17:00 – 17:30  Greenhouse (alone)\nFriday 16:45   Rooftop. The confession.",
	},
	{
		title = "Haru Seto · 3-A",
		body = "Mornings by the fountain, reading.\nLunch on the rooftop.\nAfter school in the library.\nBeing near him slows your heartbeat.\nTalking to him raises his affection.",
	},
	{
		title = "Rules of the game",
		body = "Teachers can't be beaten. Never let one see you.\nHeroes chase. Cowards run for the gate. Phone kids film you.\nBodies → incinerator, garden plot, or chop + bag + dumpster.\nBlood → mop it. Bloody uniform → shower or wardrobe.\nBloody weapon → wash it at a sink or burn it.\nPolice arrive " .. "~2.5 min after a call. Don't be the evidence.",
	},
	{
		title = "Controls",
		body = "WASD move · Shift run · C crouch (sneak)\nE / F interact · Click attack with a weapon\nTab phone · 1-9 hotbar",
	},
}

return Story

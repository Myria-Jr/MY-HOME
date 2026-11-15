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
	-- the loud ones
	{ id = "Kotone", name = "Kotone Mizuhara", gender = "F", hair = "Twintails", hairColor = Color3.fromRGB(250, 220, 140), eyeColor = Color3.fromRGB(70, 140, 230), persona = "Gossip", class = "2A", morning = "Lobby", lunch = "Cafeteria", club = "Music" },
	{ id = "Yuna", name = "Yuna Shiraishi", gender = "F", hair = "Bob", hairColor = Color3.fromRGB(240, 150, 190), eyeColor = Color3.fromRGB(220, 90, 150), persona = "Gossip", class = "1B", morning = "Fountain", lunch = "Courtyard", club = "Art" },
	-- delinquents: white shirts, no blazer, fast and not scared of you
	{ id = "Daisuke", name = "Daisuke Kurokawa", gender = "M", hair = "Spiky", hairColor = Color3.fromRGB(235, 220, 170), eyeColor = Color3.fromRGB(150, 110, 50), persona = "Delinquent", class = "3A", morning = "Field", lunch = "Roof", club = "Field", top = Color3.fromRGB(236, 236, 240), height = 1.06 },
	{ id = "Tetsu", name = "Tetsu Ogawa", gender = "M", hair = "Short", hairColor = Color3.fromRGB(220, 110, 40), eyeColor = Color3.fromRGB(120, 70, 30), persona = "Delinquent", class = "1B", morning = "Field", lunch = "Roof", club = "Gym", top = Color3.fromRGB(236, 236, 240), height = 1.03 },
	-- occult club: pale, quiet, notices everything and tells no one
	{ id = "Akane", name = "Akane Fujisaki", gender = "F", hair = "Long", hairColor = Color3.fromRGB(14, 12, 18), eyeColor = Color3.fromRGB(120, 60, 160), persona = "Coward", class = "1B", morning = "Library", lunch = "Roof", club = "Library", skin = Color3.fromRGB(246, 232, 228) },
	-- the one who asks questions
	{ id = "Ryo", name = "Ryo Amamiya", gender = "M", hair = "Messy", hairColor = Color3.fromRGB(30, 34, 56), eyeColor = Color3.fromRGB(150, 160, 180), glasses = true, prop = "Notebook", persona = "Detective", class = "2A", morning = "Lobby", lunch = "Cafeteria", club = "Library" },
}

-- ── Background students (generated, deterministic) ──────────────────────────
do
	local FIRST_F = { "Yuki", "Saki", "Mai", "Rika", "Natsu", "Kana", "Momo", "Haruka", "Chika", "Ayame", "Tsubaki", "Hotaru", "Izumi", "Nao", "Fumika", "Shiori" }
	local FIRST_M = { "Ryota", "Kazuki", "Yuma", "Hayato", "Tomoya", "Naoki", "Kenji", "Taiga", "Akira", "Jun", "Masaki", "Kosuke", "Shota", "Daiki" }
	local LAST = { "Aoki", "Fukuda", "Goto", "Hirano", "Iwasaki", "Kimura", "Maeda", "Nishida", "Ogura", "Sato", "Tamura", "Ueno", "Watanabe", "Yoshida", "Kondo", "Matsui", "Noguchi", "Shimizu" }
	local HAIR_F = { "Long", "Ponytail", "Twintails", "Bob", "Bun", "Long" }
	local HAIR_M = { "Short", "Spiky", "Messy", "Short" }
	local COLORS = {
		Color3.fromRGB(22, 18, 20), Color3.fromRGB(40, 28, 22), Color3.fromRGB(70, 46, 30), Color3.fromRGB(96, 64, 40),
		Color3.fromRGB(30, 26, 34), Color3.fromRGB(120, 80, 50), Color3.fromRGB(200, 170, 120), Color3.fromRGB(60, 40, 70),
	}
	local EYES = { Color3.fromRGB(70, 50, 40), Color3.fromRGB(90, 60, 40), Color3.fromRGB(60, 90, 140), Color3.fromRGB(70, 120, 80), Color3.fromRGB(40, 40, 50) }
	local PERSONAS = { "Coward", "Coward", "Coward", "Coward", "Coward", "Hero", "Phone", "Gossip" }
	local MORNING = { "Courtyard", "Fountain", "Lobby", "Gate", "Field" }
	local LUNCH = { "Cafeteria", "Courtyard", "Roof", "Fountain" }
	local CLUBS = { "Art", "Music", "Science", "Library", "Gym", "Field", "Garden", "Cafeteria" }
	local seed = 20261114
	local function rnd(n: number): number
		seed = (seed * 1103515245 + 12345) % 2147483648
		return (seed % n) + 1
	end
	local used = {}
	for _, d in Story.Students do
		used[d.name] = true
	end
	Story.Extras = {}
	for _, class in { "1A", "2B", "3B" } do
		for k = 1, 8 do
			local female = (k % 2 == 1)
			local name
			repeat
				name = (female and FIRST_F[rnd(#FIRST_F)] or FIRST_M[rnd(#FIRST_M)]) .. " " .. LAST[rnd(#LAST)]
			until not used[name]
			used[name] = true
			table.insert(Story.Extras, {
				id = "X" .. class .. k,
				name = name,
				gender = female and "F" or "M",
				hair = female and HAIR_F[rnd(#HAIR_F)] or HAIR_M[rnd(#HAIR_M)],
				hairColor = COLORS[rnd(#COLORS)],
				eyeColor = EYES[rnd(#EYES)],
				persona = PERSONAS[rnd(#PERSONAS)],
				class = class,
				morning = MORNING[rnd(#MORNING)],
				lunch = LUNCH[rnd(#LUNCH)],
				club = CLUBS[rnd(#CLUBS)],
			})
		end
	end
end

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
	{ id = "Hoshikawa", name = "Ms. Hoshikawa", gender = "F", hair = "Ponytail", hairColor = Color3.fromRGB(60, 40, 30), eyeColor = Color3.fromRGB(80, 60, 50), persona = "Teacher", class = "1A", height = 1.05 },
	{ id = "Sakaguchi", name = "Mr. Sakaguchi", gender = "M", hair = "Short", hairColor = Color3.fromRGB(40, 36, 36), eyeColor = Color3.fromRGB(50, 50, 55), persona = "Teacher", class = "2B", height = 1.12, glasses = true },
	{ id = "Nonomura", name = "Ms. Nonomura", gender = "F", hair = "Bob", hairColor = Color3.fromRGB(110, 70, 50), eyeColor = Color3.fromRGB(90, 70, 50), persona = "Teacher", class = "3B", height = 1.04 },
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
	Scream = { "AAAAAAH!!", "SOMEONE HELP!!", "OH MY GOD!!", "NO NO NO NO!!", "MURDER!!", "S-STAY AWAY!!", "HELP ME!!", "PLEASE NO!!", "WHY?!", "SHE'S GOT A KNIFE!!" },
	Dying = { "please…", "why…", "mom…", "I can't… breathe…", "it's cold…" },
	Corpse = { "Is— is that a body?!", "SHE'S NOT MOVING!", "Call the police!!", "Blood… so much blood…" },
	Blood = { "Is that… blood?", "Why is the floor red?", "Something's wrong here…" },
	Bloody = { "Why are you covered in blood?!", "Nyx…? What happened to you?", "D-don't come closer!" },
	Weapon = { "Why are you carrying that?", "Uh… is that a weapon?", "That's creepy, Nyx." },
	Creepy = { "Why is she smiling like that?", "Nyx is giving me the creeps.", "Are you… okay?" },
	Phone = { "*click* Got you.", "I'm recording this!", "Smile for the police." },
	Hero = { "STOP RIGHT THERE!", "You're not getting away!", "I've got you!" },
	TeacherChase = { "AMANE! STOP!", "Don't move!", "Somebody call the police!" },
	Haunted = { "Where is everyone…?", "Has anyone seen my friend?" },
	Gossip = { "OMG did you SEE that?!", "Everyone's gonna hear about this.", "Nyx is SO weird, I'm telling everyone.", "Group chat. Now." },
	Delinquent = { "You picked the wrong guy.", "Oi! Drop it!", "You're done, creep!" },
	Detective = {
		"Where were you during lunch, Nyx?",
		"Interesting. Very interesting.",
		"I'm writing this down.",
		"People don't just disappear.",
		"Your story keeps changing, you know.",
		"Mystery Club rule one: the quiet ones always know something.",
	},
	DetectiveTalk = {
		"Mystery Club meets in the library. We have one member. Me.",
		"Did you know the black roses only grew after the old incinerator was built?",
		"You look like someone who keeps secrets. I like that. Professionally.",
	},
	Dodge = { "I KNEW IT!", "Nice try.", "Not today, Amane!" },
}

-- ── Conversations between classmates ─────────────────────────────────────────
-- A / B = the two speakers. {missing} = someone who disappeared, {haru}, {mirei}
-- when: "always" · "rumor" (Mirei hasn't confessed yet) · "missing" (someone is gone) · "friday"
Story.Conversations = {
	{ when = "always", lines = {
		{ "A", "Did you finish the essay for Okada?" },
		{ "B", "I wrote four hundred words about my cat." },
		{ "A", "The topic was the Meiji Restoration." },
		{ "B", "My cat is very historical." },
	} },
	{ when = "always", lines = {
		{ "A", "The vending machine ate my coins again." },
		{ "B", "You have to kick it on the left side. Gently. Like a horse." },
		{ "A", "I'm not kicking a horse." },
	} },
	{ when = "always", lines = {
		{ "A", "Have you noticed the new girl in 2-A?" },
		{ "B", "Nyx? She's… quiet." },
		{ "A", "She smiled at me yesterday. I think. It was hard to tell." },
		{ "B", "Lucky you. She just stares at me." },
	} },
	{ when = "always", lines = {
		{ "A", "If you could delete one class forever, which one?" },
		{ "B", "PE. Kuroda makes us run in the rain." },
		{ "A", "Kuroda runs in the rain FOR FUN." },
	} },
	{ when = "always", lines = {
		{ "A", "Okay, ranking the cafeteria food. Go." },
		{ "B", "Melon bread, then curry, then whatever Tuesday was." },
		{ "A", "Tuesday was soup." },
		{ "B", "Was it?" },
	} },
	{ when = "always", lines = {
		{ "A", "I heard the roses by the gate turn black at night." },
		{ "B", "They're just dark red. You need glasses." },
		{ "A", "Then why do they call it Kurobara?" },
		{ "B", "…Okay, that's a good point." },
	} },
	{ when = "rumor", lines = {
		{ "A", "Is it true? Mirei's confessing to Haru on Friday?" },
		{ "B", "On the rooftop. At sunset. She's been planning for weeks." },
		{ "A", "That's so romantic I might throw up." },
		{ "B", "He's definitely going to say yes." },
	} },
	{ when = "rumor", lines = {
		{ "A", "Mirei bought a new ribbon just for Friday." },
		{ "B", "And she's growing lilies because Haru likes them." },
		{ "A", "Some people have all the luck." },
	} },
	{ when = "rumor", lines = {
		{ "A", "Haru reads by the fountain every single morning." },
		{ "B", "Same book too. I think he just likes the fountain." },
		{ "A", "Or someone who walks past the fountain…" },
	} },
	{ when = "missing", lines = {
		{ "A", "Has anyone heard from {missing}?" },
		{ "B", "Not since yesterday. The teachers won't say anything." },
		{ "A", "Their bag was still in the classroom." },
		{ "B", "Stop. You're scaring me." },
	} },
	{ when = "missing", lines = {
		{ "A", "My mom says I should walk home in a group now." },
		{ "B", "Because of {missing}?" },
		{ "A", "Because of {missing}." },
	} },
	{ when = "missing", lines = {
		{ "A", "Ryo from the Mystery Club keeps asking everyone questions." },
		{ "B", "He asked me what time I left yesterday. Like a cop." },
		{ "A", "Do you think he knows something?" },
	} },
	{ when = "friday", lines = {
		{ "A", "Today's the day! Mirei's big confession!" },
		{ "B", "I'm going up to the roof after school to watch." },
		{ "A", "You can't just watch someone confess!" },
		{ "B", "Watch me." },
	} },
}

-- What teachers say while they teach (only heard if you skip class)
Story.Lecture = {
	"Page one hundred and twelve, everyone.",
	"Eyes on the board, not on your phones.",
	"Who can tell me what a metaphor is? Anyone?",
	"This will be on the test. Yes, all of it.",
	"Kenta, if you're asleep, please snore quieter.",
	"The answer is not 'Haru Seto'. Focus.",
	"An empty seat… has anyone seen Amane?",
}

-- PA announcements at the school bell
Story.PA = {
	Class1 = "Good morning, Kurobara Academy. First period begins now. Please go to your classrooms.",
	Lunch = "Lunch break has started. Please do not run in the hallways.",
	Class2 = "Lunch break is over. Afternoon classes begin now.",
	AfterSchool = "Classes are over for today. Club activities may begin. Please stay safe.",
	Leaving = "The school will close soon. Please head home.",
}

-- Nyx's inner voice
Story.Thoughts = {
	Morning = {
		"Monday. Five days. My heart is already counting.",
		"Tuesday. Mirei laughed yesterday. I could hear it through two walls.",
		"Wednesday. Halfway. Everything feels very loud today.",
		"Thursday. Tomorrow she climbs those stairs. Unless she doesn't.",
		"Friday. Sunset is a deadline.",
	},
	Haru = { "He's right there. Breathe, Nyx. Breathe.", "He tucked his hair behind his ear. I'll remember that forever.", "If I stand here long enough, maybe he'll look up." },
	Kill = { "It's quiet now.", "Was that me? …That was me.", "One less voice in the hallway.", "My hands are shaking. No. They're warm." },
	Unstable = { "Too loud. Everything is too loud.", "Smile. Smile. Smile.", "Why is everyone looking at me?" },
	Police = { "Stay calm. Clean. Smile. You're just a student.", "Sirens. Don't run. Running is guilty." },
	Jealous = { "She's standing too close to him.", "She touched his sleeve. She TOUCHED his sleeve.", "That laugh. I want it to stop." },
	Ryo = { "Ryo is watching. Ryo is always watching.", "He writes everything down. Everything." },
}

-- ── Talking to people (dialogue choices) ─────────────────────────────────────
Story.Dialogue = {
	Greet = { "Oh, hey Nyx.", "Hi!", "What's up?", "Hey. You're Nyx, right?", "Morning!" },
	GreetTeacher = { "Yes, Amane?", "Can I help you?", "Make it quick, Amane." },
	GreetHaru = { "Hey, Nyx.", "Oh — hi.", "You again. …That's not a bad thing." },
	GreetMirei = { "Nyx! Hiii!", "Oh, hey! Love your hair today!" },
	GreetRyo = { "Amane. Just the person I was thinking about.", "Ah. Nyx." },
	ComplimentOk = { "Aww, thanks!", "Really? You think so?", "That's sweet of you.", "Stop it, you're making me blush." },
	ComplimentCreepy = { "Uh… thanks? Why are you smiling like that?", "…Okay. I have to go.", "That was weirdly intense, Nyx." },
	AskMissing = { "Don't. It's creepy. People don't just vanish.", "I heard the police found nothing.", "I can't sleep since {missing} disappeared.", "My parents want to pull me out of school." },
	FollowYes = { "Sure, lead the way.", "Okay, but quick.", "Ooh, a secret? Fine." },
	FollowNo = { "Uh… no thanks.", "I'd rather not.", "You're kinda scaring me, Nyx." },
	FollowClass = { "Sorry, I have to get to class!", "Class is starting, gotta go!" },
	Wait = { "Okay, I'll wait here.", "Don't take too long.", "Sure. Waiting." },
	Bye = { "See you.", "Bye!", "Later, Nyx." },
	Where = {
		Fountain = "by the fountain", FountainBench = "on the bench by the fountain", Roof = "up on the roof", RoofHaru = "up on the roof",
		RoofConfess = "on the roof", Library = "in the library", Seat = "in class", Greenhouse = "in the greenhouse, alone",
		Garden = "in the garden with the gardening club", Courtyard = "in the courtyard", Cafeteria = "in the cafeteria",
		Lobby = "near the entrance", Gate = "at the gate", Exit = "on the way home", Away = "not at school right now",
	},
	HaruBook = { "It's about a lighthouse keeper who talks to the sea.", "A mystery. I already know who did it. I'm reading it anyway.", "Poetry. Don't tell anyone." },
	HaruMirei = { "Mirei? She's nice. Really… energetic.", "She keeps asking if I'm free on Friday. I don't know why.", "We grew up on the same street. She's like a little sister." },
	HaruCompliment = { "…Thanks. Nobody ever says that.", "You're a strange one, Nyx. I mean that nicely.", "Heh. Okay. That made my day." },
	MireiFriday = { "It's a secret! …Okay, it's about Haru. Don't tell ANYONE!", "Friday at sunset. Rooftop. Wish me luck!" },
	TeacherMissing = { "We're cooperating with the police. Please go to class.", "That's not something to gossip about, Amane." },
	RyoCase = { "Disappearances, Amane. Plural, soon, if I'm right.", "Who goes where, when. Patterns. You'd be surprised what patterns say.", "I'm not accusing anyone. Yet." },
}

-- ── Phone messages ───────────────────────────────────────────────────────────
Story.Threads = {
	{ id = "mom", name = "Mom", icon = "🏠", seed = { { "Mom", "Don't forget your bento! Love you ♥" }, { "Nyx", "ok" } } },
	{ id = "class", name = "2-A Class Chat", icon = "🏫", seed = {
		{ "Kotone", "who's going to the festival on friday??" },
		{ "Ren", "me" },
		{ "Aoi", "Me too!!" },
		{ "Kotone", "omg did u hear about mirei and haru 👀" },
	} },
	{ id = "unknown", name = "Unknown Number", icon = "❔", seed = {} },
	{ id = "haru", name = "Haru Seto", icon = "📖", seed = {} },
}

-- server sends these when things happen ({missing} = the latest person who disappeared)
Story.Texts = {
	Day = {
		{ { "mom", "Mom", "Have a good first day this week! Eat something!" } },
		{ { "mom", "Mom", "You came home late yesterday. Everything ok?" } },
		{ { "mom", "Mom", "I'm working late tonight. There's curry in the fridge." } },
		{ { "mom", "Mom", "You seem happier lately. Is it a boy? 👀" } },
		{ { "mom", "Mom", "Enjoy the festival today! Take pictures!" } },
	},
	FirstKill = {
		{ "class", "Kotone", "has anyone heard from {missing}??" },
		{ "class", "Aoi", "their phone goes straight to voicemail" },
		{ "class", "Ren", "probably skipping lol" },
	},
	Police = { { "class", "Ren", "why are there cops at school" }, { "class", "Kotone", "SOMETHING HAPPENED. i'm scared" } },
	RivalDown = { { "class", "Kotone", "MIREI is missing too?? this isn't funny anymore" }, { "class", "Aoi", "please everyone walk home in groups" } },
	Suspicion = { { "unknown", "Unknown", "I know where you were when it happened, Amane." }, { "unknown", "Unknown", "Every pattern has a center. — R" } },
	Haru = { { "haru", "Haru", "Hey, it's Haru. Got your number from the class list." }, { "haru", "Haru", "See you by the fountain tomorrow?" } },
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
		title = "Ryo Amamiya · 2-A",
		body = "Mystery Club. Sits right behind you.\nOnce someone goes missing, he starts following you.\nWatch the 🔍 meter. At 100 he goes to the teachers.\nHe sees attacks coming — only from behind.",
	},
	{
		title = "Rules of the game",
		body = "Teachers can't be beaten. Never let one see you.\nHeroes chase. Cowards run for the gate. Phone kids film you.\nBodies → incinerator, garden plot, or chop + bag + dumpster.\nBlood → mop it. Bloody uniform → shower or wardrobe.\nBloody weapon → wash it at a sink or burn it.\nPolice arrive " .. "~2.5 min after a call. Don't be the evidence.",
	},
	{
		title = "Controls",
		body = "WASD move · Shift run · C crouch (sneak)\nE / F interact · G drop · Click attack\nV switch ego / 3rd person · Tab phone · 1-9 hotbar\nTouch: buttons on the right, context buttons above them",
	},
}

return Story

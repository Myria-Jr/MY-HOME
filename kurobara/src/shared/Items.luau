-- KUROBARA · Item definitions (shared by server + client)

local Items = {}

-- hits      seconds into the clip where a blow lands (must match Anim.Clips[clip])
-- blood     spray multiplier per hit
-- bloodiness how much of the victim ends up on Nyx's uniform (0-100)
-- suspicion 0..1 – how alarming it is to see Nyx holding it
-- kneelAfter victim drops to their knees after this hit
-- focus     where the blows land: "Torso" | "Neck" | "Head"
Items.Weapons = {
	BoxCutter = {
		display = "Box Cutter", clip = "BoxCutter", hits = { 0.3, 0.7 }, duration = 1.4,
		blood = 0.9, bloodiness = 22, suspicion = 0.35, focus = "Neck", victimClip = "Clutch",
		sound = "Slash", spawn = "Art", color = Color3.fromRGB(240, 200, 40),
	},
	KitchenKnife = {
		display = "Kitchen Knife", clip = "Knife", hits = { 0.33, 0.6, 0.85, 1.25 }, duration = 1.95,
		blood = 1.0, bloodiness = 40, suspicion = 0.8, focus = "Torso", victimClip = "Grabbed",
		sound = "Stab", spawn = "Cafeteria",
	},
	Bat = {
		display = "Baseball Bat", clip = "Bat", hits = { 0.55, 1.2 }, duration = 1.8,
		blood = 0.75, bloodiness = 26, suspicion = 0.55, focus = "Head", victimClip = "Grabbed",
		kneelAfter = 1, blunt = true, sound = "Thud", spawn = "Gym",
	},
	FireAxe = {
		display = "Fire Axe", clip = "Axe", hits = { 0.68, 1.33 }, duration = 2.0,
		blood = 1.5, bloodiness = 55, suspicion = 1.0, focus = "Torso", victimClip = "Grabbed",
		kneelAfter = 1, dismember = true, sound = "Slash", spawn = "AxeCase",
	},
	Shears = {
		display = "Garden Shears", clip = "Shears", hits = { 0.35, 0.7 }, duration = 1.25,
		blood = 0.9, bloodiness = 30, suspicion = 0.65, focus = "Torso", victimClip = "Grabbed",
		sound = "Stab", spawn = "Greenhouse",
	},
}

Items.Tools = {
	Mop = { display = "Mop", spawn = "Janitor", suspicion = 0 },
	Shovel = { display = "Shovel", spawn = "Greenhouse2", suspicion = 0.1 },
	TrashBags = { display = "Trash Bags", spawn = "Janitor2", suspicion = 0 },
}

-- Kitchen knife at home too (a second copy)
Items.ExtraSpawns = {
	{ item = "KitchenKnife", spawn = "HomeKitchen" },
}

function Items.Get(name: string)
	return Items.Weapons[name] or Items.Tools[name]
end

function Items.IsWeapon(name: string): boolean
	return Items.Weapons[name] ~= nil
end

return Items

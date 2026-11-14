-- KUROBARA · Tools: weapon/utility models, pickups around the map, daily respawns

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Items = require(Shared.Items)
local Config = require(Shared.Config)
local Util = require(Shared.Util)

local ItemService = {}
local S

local METAL = Color3.fromRGB(205, 210, 218)

-- Parts are positioned relative to the Handle. Blades extend along the Handle's -Z
-- (the same direction as the arm), so they point forward while the default "tool" pose holds them.
local function part(tool: Tool, name: string, size: Vector3, offset: CFrame, color: Color3, mat: Enum.Material?, blade: boolean?): Part
	local handle = tool:FindFirstChild("Handle") :: BasePart
	local p = Instance.new("Part")
	p.Name = name
	p.Size = size
	p.Color = color
	p.Material = mat or Enum.Material.SmoothPlastic
	p.CanCollide = false
	p.CanQuery = false
	p.CanTouch = false
	p.Massless = true
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	p.CFrame = handle.CFrame * offset
	if blade then
		p:SetAttribute("Blade", true)
		p.Reflectance = mat == Enum.Material.Metal and 0.25 or 0
	end
	Util.weld(handle, p)
	p.Parent = tool
	return p
end

local Builders = {}

function Builders.BoxCutter(tool)
	part(tool, "Blade", Vector3.new(0.05, 0.16, 0.42), CFrame.new(0, 0.03, -0.62), METAL, Enum.Material.Metal, true)
end
function Builders.KitchenKnife(tool)
	part(tool, "Bolster", Vector3.new(0.12, 0.34, 0.08), CFrame.new(0, 0.03, -0.43), METAL, Enum.Material.Metal)
	part(tool, "Blade", Vector3.new(0.05, 0.36, 1.25), CFrame.new(0, 0.06, -1.08), METAL, Enum.Material.Metal, true)
	local tip = Instance.new("WedgePart")
	tip.Name = "Tip"
	tip.Size = Vector3.new(0.05, 0.36, 0.4)
	tip.Color = METAL
	tip.Material = Enum.Material.Metal
	tip.CanCollide = false
	tip.CanQuery = false
	tip.Massless = true
	tip.CFrame = (tool:FindFirstChild("Handle") :: BasePart).CFrame * CFrame.new(0, 0.06, -1.9) * CFrame.Angles(0, math.rad(180), 0)
	tip:SetAttribute("Blade", true)
	Util.weld(tool:FindFirstChild("Handle") :: BasePart, tip)
	tip.Parent = tool
end
function Builders.Bat(tool)
	part(tool, "Barrel", Vector3.new(0.44, 0.44, 2.3), CFrame.new(0, 0, -1.75), Color3.fromRGB(214, 176, 120), Enum.Material.Wood, true)
	part(tool, "Cap", Vector3.new(0.46, 0.46, 0.1), CFrame.new(0, 0, -2.92), Color3.fromRGB(190, 150, 100), Enum.Material.Wood, true)
	part(tool, "Knob", Vector3.new(0.34, 0.34, 0.12), CFrame.new(0, 0, 0.5), Color3.fromRGB(40, 30, 26))
end
function Builders.FireAxe(tool)
	part(tool, "Shaft", Vector3.new(0.2, 0.2, 2.6), CFrame.new(0, 0, -1.7), Color3.fromRGB(150, 100, 60), Enum.Material.Wood)
	part(tool, "Head", Vector3.new(0.14, 0.8, 0.62), CFrame.new(0, -0.28, -2.85), Color3.fromRGB(200, 24, 30), Enum.Material.SmoothPlastic, true)
	part(tool, "Edge", Vector3.new(0.09, 0.3, 0.9), CFrame.new(0, -0.78, -2.85), METAL, Enum.Material.Metal, true)
	part(tool, "Spike", Vector3.new(0.12, 0.34, 0.26), CFrame.new(0, 0.28, -2.85), Color3.fromRGB(40, 40, 44), Enum.Material.Metal)
end
function Builders.Shears(tool)
	for _, x in { -0.05, 0.05 } do
		part(tool, "Blade", Vector3.new(0.06, 0.2, 1.15), CFrame.new(x, 0, -0.98) * CFrame.Angles(0, math.rad(x * 40), 0), METAL, Enum.Material.Metal, true)
	end
	part(tool, "Pivot", Vector3.new(0.18, 0.18, 0.18), CFrame.new(0, 0, -0.45), Color3.fromRGB(40, 40, 44), Enum.Material.Metal)
end
function Builders.Mop(tool)
	part(tool, "Pole", Vector3.new(0.16, 0.16, 4.2), CFrame.new(0, 0, -2.4), Color3.fromRGB(70, 130, 200), Enum.Material.Metal)
	part(tool, "MopHead", Vector3.new(1.3, 0.35, 0.7), CFrame.new(0, 0, -4.6), Color3.fromRGB(235, 232, 225), Enum.Material.Fabric, true)
end
function Builders.Shovel(tool)
	part(tool, "Shaft", Vector3.new(0.18, 0.18, 3.2), CFrame.new(0, 0, -2), Color3.fromRGB(150, 100, 60), Enum.Material.Wood)
	part(tool, "Spade", Vector3.new(0.9, 0.08, 1.0), CFrame.new(0, 0, -3.95), Color3.fromRGB(120, 124, 130), Enum.Material.Metal, true)
end
function Builders.TrashBags(_tool) end

local HANDLES = {
	BoxCutter = { Vector3.new(0.26, 0.22, 0.85), Color3.fromRGB(240, 200, 40), Enum.Material.SmoothPlastic },
	KitchenKnife = { Vector3.new(0.2, 0.3, 0.8), Color3.fromRGB(30, 26, 28), Enum.Material.SmoothPlastic },
	Bat = { Vector3.new(0.3, 0.3, 1.2), Color3.fromRGB(60, 40, 30), Enum.Material.Fabric },
	FireAxe = { Vector3.new(0.24, 0.24, 0.9), Color3.fromRGB(150, 100, 60), Enum.Material.Wood },
	Shears = { Vector3.new(0.3, 0.26, 0.9), Color3.fromRGB(60, 140, 70), Enum.Material.SmoothPlastic },
	Mop = { Vector3.new(0.18, 0.18, 0.9), Color3.fromRGB(70, 130, 200), Enum.Material.Metal },
	Shovel = { Vector3.new(0.3, 0.2, 0.8), Color3.fromRGB(30, 30, 34), Enum.Material.SmoothPlastic },
	TrashBags = { Vector3.new(0.7, 0.7, 0.7), Color3.fromRGB(20, 20, 24), Enum.Material.SmoothPlastic },
}

function ItemService.MakeTool(id: string): Tool
	local def = Items.Get(id)
	local tool = Instance.new("Tool")
	tool.Name = def.display
	tool.ToolTip = def.display
	tool.CanBeDropped = false
	tool.RequiresHandle = true
	tool:SetAttribute("ItemId", id)
	tool:SetAttribute("Weapon", Items.IsWeapon(id))
	tool:SetAttribute("Suspicion", def.suspicion or 0)
	local h = HANDLES[id]
	local handle = Instance.new("Part")
	handle.Name = "Handle"
	handle.Size = h[1]
	handle.Color = h[2]
	handle.Material = h[3]
	handle.CanCollide = false
	handle.CanQuery = false
	handle.Massless = true
	handle.TopSurface = Enum.SurfaceType.Smooth
	handle.BottomSurface = Enum.SurfaceType.Smooth
	handle.Parent = tool
	if id == "TrashBags" then
		Util.mesh(handle, Enum.MeshType.Sphere)
	end
	Builders[id](tool)
	if Items.IsWeapon(id) then
		S.Combat.Hook(tool)
	else
		S.Bodies.HookUtility(tool)
	end
	return tool
end

-- ── Pickups ──────────────────────────────────────────────────────────────────
local pickups: { [string]: Model } = {}
local spawnList = {}

local function buildSpawnList()
	spawnList = {}
	for id, def in Items.Weapons do
		table.insert(spawnList, { key = id, item = id, spawn = def.spawn })
	end
	for id, def in Items.Tools do
		table.insert(spawnList, { key = id, item = id, spawn = def.spawn })
	end
	for i, extra in Items.ExtraSpawns do
		table.insert(spawnList, { key = extra.item .. "_" .. i, item = extra.item, spawn = extra.spawn })
	end
end

local function existsAnywhere(key: string): boolean
	if pickups[key] and pickups[key].Parent then
		return true
	end
	for _, player in Players:GetPlayers() do
		for _, container in { player:FindFirstChildOfClass("Backpack"), player.Character } do
			if container then
				for _, t in container:GetChildren() do
					if t:IsA("Tool") and t:GetAttribute("SpawnKey") == key then
						return true
					end
				end
			end
		end
	end
	return false
end

function ItemService.SpawnPickup(entry)
	local cf = S.World.ItemSpawns[entry.spawn]
	if not cf then
		warn("[Kurobara] no item spawn for", entry.spawn)
		return
	end
	local tool = ItemService.MakeTool(entry.item)
	local model = Instance.new("Model")
	model.Name = "Pickup_" .. entry.key
	for _, p in tool:GetChildren() do
		if p:IsA("BasePart") then
			p.Parent = model
		end
	end
	tool:Destroy()
	local handle = model:FindFirstChild("Handle") :: BasePart
	model.PrimaryPart = handle
	model:PivotTo(cf)
	for _, p in model:GetDescendants() do
		if p:IsA("BasePart") then
			p.Anchored = true
		end
	end
	if entry.spawn == "AxeCase" then
		local glassPane = workspace.Map:FindFirstChild("AxeCaseGlass", true) :: BasePart?
		if glassPane then
			glassPane.Transparency = 0.6
			glassPane.CanCollide = true
		end
	end
	local def = Items.Get(entry.item)
	local prompt = Instance.new("ProximityPrompt")
	prompt.ActionText = "Take"
	prompt.ObjectText = def.display
	prompt.KeyboardKeyCode = Enum.KeyCode.E
	prompt.MaxActivationDistance = 7
	prompt.RequiresLineOfSight = false
	prompt.Style = Enum.ProximityPromptStyle.Custom
	prompt.Parent = handle
	prompt.Triggered:Connect(function(player)
		if not model.Parent then
			return
		end
		local st = S.GameState.Get(player)
		if not st or st.Busy then
			return
		end
		local tool2 = ItemService.MakeTool(entry.item)
		tool2:SetAttribute("SpawnKey", entry.key)
		tool2.Parent = player:FindFirstChildOfClass("Backpack")
		model:Destroy()
		pickups[entry.key] = nil
		if entry.spawn == "AxeCase" then
			local glassPane = workspace.Map:FindFirstChild("AxeCaseGlass", true) :: BasePart?
			if glassPane then
				glassPane.Transparency = 1
				glassPane.CanCollide = false
			end
			local char = player.Character
			Util.playSound(Config.Sounds.Glass, char and char:FindFirstChild("Head"), 0.8)
		end
		S.Net.Notify(player, "Got " .. def.display .. (Items.IsWeapon(entry.item) and " · click to attack" or ""), "info")
	end)
	model.Parent = workspace.Items
	pickups[entry.key] = model
end

function ItemService.RespawnMissing()
	for _, entry in spawnList do
		if not existsAnywhere(entry.key) then
			ItemService.SpawnPickup(entry)
		end
	end
end

function ItemService.ResetAll()
	for _, m in pickups do
		m:Destroy()
	end
	pickups = {}
	workspace.Items:ClearAllChildren()
	for _, player in Players:GetPlayers() do
		for _, container in { player:FindFirstChildOfClass("Backpack"), player.Character } do
			if container then
				for _, t in container:GetChildren() do
					if t:IsA("Tool") then
						t:Destroy()
					end
				end
			end
		end
	end
	ItemService.RespawnMissing()
end

function ItemService.Init(services)
	S = services
	buildSpawnList()
end

-- helpers for other systems
function ItemService.Equipped(player: Player): Tool?
	local char = player.Character
	return char and char:FindFirstChildOfClass("Tool")
end

function ItemService.HasItem(player: Player, id: string): Tool?
	for _, container in { player.Character, player:FindFirstChildOfClass("Backpack") } do
		if container then
			for _, t in container:GetChildren() do
				if t:IsA("Tool") and t:GetAttribute("ItemId") == id then
					return t
				end
			end
		end
	end
	return nil
end

function ItemService.BloodyWeaponOn(player: Player): Tool?
	for _, container in { player.Character, player:FindFirstChildOfClass("Backpack") } do
		if container then
			for _, t in container:GetChildren() do
				if t:IsA("Tool") and t:GetAttribute("Bloody") then
					return t
				end
			end
		end
	end
	return nil
end

return ItemService

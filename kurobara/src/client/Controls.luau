-- KUROBARA · Input: run, sneak, phone (keyboard + touch buttons)

local ContextActionService = game:GetService("ContextActionService")
local StarterGui = game:GetService("StarterGui")

local Controls = {}
local remote: RemoteEvent
local HUD
local crouching = false

local function run(_, state: Enum.UserInputState)
	if state == Enum.UserInputState.Begin then
		remote:FireServer("Run", true)
		if crouching then
			crouching = false
		end
	elseif state == Enum.UserInputState.End or state == Enum.UserInputState.Cancel then
		remote:FireServer("Run", false)
	end
	return Enum.ContextActionResult.Pass
end

local function crouch(_, state: Enum.UserInputState)
	if state == Enum.UserInputState.Begin then
		crouching = not crouching
		remote:FireServer("Crouch", crouching)
	end
	return Enum.ContextActionResult.Sink
end

local function phone(_, state: Enum.UserInputState)
	if state == Enum.UserInputState.Begin then
		HUD.TogglePhone()
	end
	return Enum.ContextActionResult.Sink
end

function Controls.Enable(on: boolean)
	if on then
		ContextActionService:BindAction("KB_Run", run, true, Enum.KeyCode.LeftShift, Enum.KeyCode.ButtonL3)
		ContextActionService:BindAction("KB_Crouch", crouch, true, Enum.KeyCode.C, Enum.KeyCode.ButtonR3)
		ContextActionService:BindAction("KB_Phone", phone, true, Enum.KeyCode.Tab, Enum.KeyCode.ButtonSelect)
		pcall(function()
			ContextActionService:SetTitle("KB_Run", "Run")
			ContextActionService:SetTitle("KB_Crouch", "Sneak")
			ContextActionService:SetTitle("KB_Phone", "Phone")
			ContextActionService:SetPosition("KB_Run", UDim2.new(1, -170, 1, -150))
			ContextActionService:SetPosition("KB_Crouch", UDim2.new(1, -230, 1, -100))
			ContextActionService:SetPosition("KB_Phone", UDim2.new(1, -110, 1, -210))
		end)
	else
		for _, a in { "KB_Run", "KB_Crouch", "KB_Phone" } do
			ContextActionService:UnbindAction(a)
		end
	end
end

function Controls.Init(r: RemoteEvent, hud)
	remote = r
	HUD = hud
	-- no reset button, no player list; keep the hotbar
	task.spawn(function()
		for _ = 1, 20 do
			local ok = pcall(function()
				StarterGui:SetCore("ResetButtonCallback", false)
			end)
			if ok then
				break
			end
			task.wait(0.5)
		end
	end)
	pcall(function()
		StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.PlayerList, false)
		StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Health, false)
		StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.EmotesMenu, false)
	end)
end

return Controls

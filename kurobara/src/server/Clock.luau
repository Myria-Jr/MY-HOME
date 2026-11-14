-- KUROBARA · In-game clock, school periods and the Friday deadline

local Lighting = game:GetService("Lighting")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)

local Clock = {}
Clock.Minutes = Config.Time.DayStart
Clock.Running = false
local S
local periodIndex = 1

function Clock.Init(services)
	S = services
end

local function indexAt(minute: number): number
	local idx = 1
	for i, p in Config.Periods do
		if minute >= p.start then
			idx = i
		end
	end
	return idx
end

function Clock.Period()
	return Config.Periods[periodIndex]
end

function Clock.NextPeriodStart(): number
	local nxt = Config.Periods[periodIndex + 1]
	return nxt and nxt.start or Config.Time.DayEnd
end

local function publish()
	workspace:SetAttribute("Clock", Clock.Minutes)
	workspace:SetAttribute("Period", Config.Periods[periodIndex].label)
	workspace:SetAttribute("PeriodId", Config.Periods[periodIndex].id)
end

local function onPeriodChanged()
	local p = Config.Periods[periodIndex]
	publish()
	S.Net.Screen(nil, "Bell", { label = p.label, class = p.class == true })
	if p.class then
		S.Net.Notify(nil, "🔔 Class is starting. Your seat is in 2-A.", "info")
	elseif p.id == "Lunch" then
		S.Net.Notify(nil, "🔔 Lunch break.", "info")
	elseif p.id == "AfterSchool" then
		S.Net.Notify(nil, "🔔 School's out. Clubs are starting.", "info")
	elseif p.id == "Leaving" then
		S.Net.Notify(nil, "Everyone's heading home.", "info")
	end
end

function Clock.Set(minute: number)
	Clock.Minutes = minute
	local idx = indexAt(minute)
	local changed = idx ~= periodIndex
	periodIndex = idx
	Lighting.ClockTime = minute / 60
	S.Builder.TimeOfDay(minute)
	publish()
	if changed then
		onPeriodChanged()
	end
end

function Clock.Start()
	publish()
	local acc = 0
	RunService.Heartbeat:Connect(function(dt)
		if not Clock.Running then
			return
		end
		Clock.Minutes += dt * Config.Time.MinutesPerSecond
		Lighting.ClockTime = Clock.Minutes / 60
		local idx = indexAt(Clock.Minutes)
		if idx ~= periodIndex then
			periodIndex = idx
			onPeriodChanged()
		end
		acc += dt
		if acc > 0.25 then
			acc = 0
			workspace:SetAttribute("Clock", Clock.Minutes)
			S.Builder.TimeOfDay(Clock.Minutes)
		end
		-- Friday sunset: the confession
		if S.GameState.Day == Config.ConfessionDay and Clock.Minutes >= Config.ConfessionTime and not S.GameState.RivalDown then
			Clock.Running = false
			task.spawn(S.GameState.Ending, "Bad")
			return
		end
		if Clock.Minutes >= Config.Time.DayEnd then
			Clock.Running = false
			task.spawn(S.GameState.EndDay, "Evening")
		end
	end)
end

return Clock

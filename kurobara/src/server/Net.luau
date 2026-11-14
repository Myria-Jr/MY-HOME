-- KUROBARA · thin wrappers around the remotes

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Remotes = ReplicatedStorage:WaitForChild("Remotes")

local Net = {}

-- Play a procedural clip on every client. clip = false stops that layer ("*" = all layers).
function Net.Anim(model: Model, clip: string | boolean, layer: string?, speed: number?)
	Remotes.PlayAnim:FireAllClients(model, clip, layer or "Action", speed or 1)
end

function Net.StopAnim(model: Model, layer: string?)
	Remotes.PlayAnim:FireAllClients(model, false, layer or "*", 1)
end

function Net.Notify(player: Player?, text: string, kind: string?)
	if player then
		Remotes.Notify:FireClient(player, text, kind or "info")
	else
		Remotes.Notify:FireAllClients(text, kind or "info")
	end
end

function Net.Screen(player: Player?, kind: string, data: { [string]: any }?)
	if player then
		Remotes.ScreenFX:FireClient(player, kind, data or {})
	else
		Remotes.ScreenFX:FireAllClients(kind, data or {})
	end
end

return Net

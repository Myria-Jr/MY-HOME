-- KUROBARA · Your uploaded sound IDs go here.
--
-- 1. Upload the 4 files from kurobara/audio/ to Roblox
--    (Studio → Asset Manager → Bulk Import, or create.roblox.com → Creations → Audio).
-- 2. Copy each ID and paste it below as "rbxassetid://123456789".
-- Empty strings / empty lists = silent (the game never falls back to Roblox's default sounds).

return {
	Sheet = "", -- kurobara_sfx_sheet.ogg  (all sound effects in one file)
	MusicCalm = "", -- music_calm.ogg
	MusicTension = "", -- music_tension.ogg
	MusicChase = "", -- music_chase.ogg

	-- Optional real voice recordings. Add as many IDs as you like; one is picked at random.
	-- Record yourself, or search the Creator Store (Toolbox → Audio) for "scream", "gasp", "cry".
	-- While a list is empty, the text-to-speech voice does the screaming instead.
	ScreamF = {},
	ScreamM = {},
	Gasp = {},
	NyxLaugh = {},
}

-- Adds the controller preset to the game's list once, and shows it there straight away.
local function installPreset()
    if ViewpointThirdPerson and ViewpointThirdPerson.installControllerPreset() and gamepadBinding then
        gamepadBinding:populateFromLoadedSets()
    end
end
installPreset()
Events.OnMainMenuEnter.Add(installPreset)

local wrapped = false

Events.OnGameStart.Add(function()
    if not ViewpointThirdPerson then return end
    ViewpointThirdPerson.init()
    if wrapped then return end
    wrapped = true

    -- D-pad down selects in Viewpoint's loot panel while it shows, in place of the emote wheel.
    local displayDown = ISDPadWheels.onDisplayDown
    ISDPadWheels.onDisplayDown = function(joypadData)
        if joypadData.player == 0 and ViewpointThirdPerson.lootPanelPress() then return end
        return displayDown(joypadData)
    end
    -- RB takes or does what the panel selects, in place of reloading, while it shows.
    local rbPress = ISButtonPrompt.onRBPress
    ISButtonPrompt.onRBPress = function(self)
        if self.player == 0 and ViewpointThirdPerson.lootPanelTake() then return end
        return rbPress(self)
    end
    -- Viewpoint's view keys on the controller's Back wheel, after the game's own slices.
    local addCommands = ISBackButtonWheel.addCommands
    ISBackButtonWheel.addCommands = function(self)
        addCommands(self)
        if self.playerNum ~= 0 or not (Viewpoint and Viewpoint.Keys) then return end
        local function key(id, icon)
            self:addSlice(Viewpoint.Keys.label(id), getTexture("media/ui/ViewpointThirdPerson/" .. icon .. ".png"),
                function() ViewpointThirdPerson.padKey(id) end)
        end
        local on = ViewpointThirdPerson.viewOn()
        key("keys.firstPerson", on and "view_iso" or "view_3d")
        if on then
            local third = ViewpointThirdPerson.thirdPersonOn()
            key("keys.thirdPerson", third and "to_first" or "to_third")
            if third and not getSpecificPlayer(0):getVehicle() then
                key("thirdPersonCamera.keys.swapShoulder", "shoulder")
            end
        end
    end
    local releaseDown = JoypadControllerData.onReleaseDown
    JoypadControllerData.onReleaseDown = function(self)
        if self.joypad and self.joypad.player == 0 then ViewpointThirdPerson.lootPanelRelease() end
        return releaseDown(self)
    end
end)

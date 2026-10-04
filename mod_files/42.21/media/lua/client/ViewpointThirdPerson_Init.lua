-- Adds the controller preset to the game's list once, and shows it there straight away.
local function installPreset()
    if ViewpointThirdPerson and ViewpointThirdPerson.installControllerPreset() and gamepadBinding then
        gamepadBinding:populateFromLoadedSets()
    end
end
installPreset()
Events.OnMainMenuEnter.Add(installPreset)

-- Viewpoint's loot window key opens the player's own inventory, with the cursor free, when
-- Viewpoint's loot panel is not up (Viewpoint takes the key then); again to close it. Shown the
-- way Viewpoint shows its loot window: unfolded, and put back as it was.
local inventoryOpen, inventoryWasHidden, inventoryFreed = false, false, false

-- Viewpoint - Interface hides the game's inventory window and shows its own tab for the game's
-- inventory key; this key does the same then, through its public API.
local function interfaceInventory()
    local ui = ViewpointUI
    if not (ui and ui.isActive and ui.Hub and ui.Hub.open) then return false end
    local ok, active = pcall(ui.isActive)
    if not (ok and active) then return false end
    if ui.Hub.isOpen() then
        ui.Hub.close()
    else
        ui.Hub.open("inventory")
    end
    return true
end

local function onInventoryKey(key)
    if not ViewpointThirdPerson.inventoryKey(key) then return end
    -- The key's game uses: the chat's streams and the furniture tool's mode.
    if ISChat and ISChat.focused then return end
    if getCell() and getCell():getDrag(0) then return end
    if interfaceInventory() then return end
    local inv = getPlayerInventory(0)
    if not inv then return end
    if inventoryOpen and inv:getIsVisible() then
        if inventoryWasHidden then
            inv:setVisible(false)
        elseif not inv.pin then
            inv:collapseNow()
        end
        if inventoryFreed then ViewpointThirdPerson.holdCursor() end
        inventoryOpen, inventoryFreed = false, false
        return
    end
    inventoryWasHidden = not inv:getIsVisible()
    inv:setVisible(true)
    inv.isCollapsed = false
    inv:clearMaxDrawHeight()
    inv.collapseCounter = 0
    inventoryFreed = ViewpointThirdPerson.freeCursor() or (inventoryOpen and inventoryFreed)
    inventoryOpen = true
end

local wrapped = false

Events.OnGameStart.Add(function()
    if not ViewpointThirdPerson then return end
    ViewpointThirdPerson.init()
    if wrapped then return end
    wrapped = true
    Events.OnKeyStartPressed.Add(onInventoryKey)

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

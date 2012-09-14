--[[============================================================================
main.lua
============================================================================]]--

-- Reload the script whenever this file is saved. 
-- Additionally, execute the attached function.
_AUTO_RELOAD_DEBUG = function()
  
end

-- Read from the manifest.xml file.
class "RenoiseScriptingTool" (renoise.Document.DocumentNode)
  function RenoiseScriptingTool:__init()    
    renoise.Document.DocumentNode.__init(self) 
    self:add_property("Name", "Untitled Tool")
    self:add_property("Id", "Unknown Id")
  end

local manifest = RenoiseScriptingTool()
local ok,err = manifest:load_from("manifest.xml")
local tool_name = manifest:property("Name").value
local tool_id = manifest:property("Id").value

--------------------------------------------------------------------------------
-- Preferences
--------------------------------------------------------------------------------

local options = renoise.Document.create("SuperColliderIntegrationPreferences") {
  sclang_host = "localhost"
  sclang_port = 57120
}

renoise.tool().preferences = options

--------------------------------------------------------------------------------
-- Main functions
--------------------------------------------------------------------------------

-- send an OSC message to SuperCollider stating that a song has been saved with
-- the filename of the song as it's parameter
local function send_document_saved_msg()
  local file_name = renoise.song().file_name
  
  local client, socket_error = renoise.Socket.create_client(
    options.sclang_host.value, 
    options.sclang_port.value, 
    renoise.Socket.PROTOCOL_UDP
  )
   
  if (socket_error) then
    renoise.app():show_warning(("Failed to start the " ..
      "OSC client. Error: '%s'"):format(socket_error))
    return
  end

  client:send(
    renoise.Osc.Message("/sc_renoise/document_saved", {{
      tag = "s", value = file_name
  }}))
end

-- send an OSC message to SuperCollider stating that a song has been loaded 
-- with the filename of the song as it's parameter
local function send_document_loaded_msg()
  local file_name = renoise.song().file_name
  
  local client, socket_error = renoise.Socket.create_client(
    options.sclang_host.value, 
    options.sclang_port.value, 
    renoise.Socket.PROTOCOL_UDP
  )
   
  if (socket_error) then
    renoise.app():show_warning(("Failed to start the " ..
      "OSC client. Error: '%s'"):format(socket_error))
    return
  end

  client:send(
    renoise.Osc.Message("/sc_renoise/document_loaded", {{
      tag = "s", value = file_name
  }}))
end


--------------------------------------------------------------------------------
-- Save notifiers
--------------------------------------------------------------------------------

-- invoked each time a song is successfully saved.
renoise.tool().app_saved_document_observable:add_notifier(
  function()
    send_document_saved_msg()
  end
)

-- invoked each time a song is loaded/created.
renoise.tool().app_new_document_observable:add_notifier(
  function()
    send_document_loaded_msg()
  end
)




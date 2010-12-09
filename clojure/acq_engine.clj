(ns acq-engine
  (:use [mm :only [mmc gui acq]])
  (:import [org.micromanager.api AcquisitionEngine]))


; engine

(defn snap-image [event auto-shutter]
  (if (and auto-shutter (. mmc getShutterOpen))
    (. mmc setShutterOpen true)
    (. mmc waitForDevice (. mmc getShutterDevice)))
  (. mmc snapImage)
  (if (and auto-shutter (event :close-shutter))
     (. mmc setShutterOpen false))
  (. mmc getImage))

(defn collect-burst-image []
  (while (zero? (. mmc remainingImageCount))
    (Thread/sleep 5))
  (. mmc popNextImage))   
  
(defn update-channel [event]
  (when-let [channel (event :channel)]
    (let [channel-group (. mmc getChannelGroup)
          config (.config channel)]
      (. mmc setConfig channel-group config))))
      
(defn update-slice [event]
  (when-let [slice (event :slice)]
    (when-let [focusDevice (. mmc getFocusDevice)]
      (. mmc setPosition focusDevice slice))))

(defn get-stage-positions [^MultiStagePosition msp]
  (map #(.get msp %) (range (.size msp))))

(defn update-position [event]
  (when-let [position (event :position)]
    (for [sp (get-stage-positions position)]
      (let [stage (.stageName sp)]
        (condp = (.numAxes sp)
          1 (. mmc setPosition stage (.x sp))
          2 (. mmc setXYPosition stage (.x sp) (.y sp)))))))
          
(defn run-autofocus [event]
  (when (event :use-autofocus)
    (.. gui getAutofocusManager getDevice fullFocus)))
          
(defn clock-ms []
  (quot (System/nanoTime) 1000000))

(defn acq-sleep [event last-wake-time]
  (let [sleep-time (-
                     (+ last-wake-time (event :interval-ms))
                     (clock-ms))]
    (when (pos? sleep-time)
      (Thread/sleep sleep-time)))
  (clock-ms))
  
(defn wait-for-devices [event]
  (. mmc waitForDevice (. mmc getFocusDevice))
  ;; more
  )
  
(defn run-event [event last-wake-time]
  (update-channel event)
  (update-position event)
  (let [new-wake-time (acq-sleep event last-wake-time)]
    (run-autofocus event)
    (update-slice event)
    (wait-for-devices event)
    (snap-image event)
    new-wake-time))

(defn run-events [events]
  (doall (map run-event events)))
  
(defn run-acquisition [params] 
  (println params))

(defn compute-verbose-summary [params]
   "meh")

;; AcquisitionEngine implementation



(defn create-acq-eng []
  (let [params (atom {})
       set-param! #(swap! params assoc %1 %2)
       current-state (ref {})]
    (reify AcquisitionEngine
      (abortRequest [this] nil)
      (abortRequested [this] (@current-state :abort-requested))
      (acquire [this] (run-acquisition @params))
      (addChannel [this name exp offset s8 s16 skip color] nil)
      (addChannel [this name exp do-z-stack offset s8 s16 color use] nil)
      (addImageProcessor [this processor] nil)
      (clear [this] (reset! params {}))
      (enableAutoFocus [this state] (set-param! :use-autofocus state))
      (enableChannelsSetting [this state] (set-param! :use-channels state))
      (enableFramesSetting [this state] (set-param! :use-frames state))
      (enableMultiPosition [this state] (set-param! :use-positions state))
      (enableZSliceSetting [this state] (set-param! :use-slices state))
      (getAfSkipInterval [this] (get @params :autofocus-skip-interval))
      (getAvailableGroups [this ] (get @params :available-groups))
      (getCameraConfigs [this ] (get @params :camera-configs))
      (getChannelConfigs [this ] (get @params :channel-configs))
      (getChannelGroup [this ] (get @params :channel-group))
      (getChannels [this ] (get @params :channels))
      (getCurrentFrameCount [this] (@current-state :frame))
      (getCurrentZPos [this] (@current-state :slice))
      (getDisplayMode [this ] (get @params :display-mode))
      (getFirstConfigGroup [this ] (get @params :first-config-group))
      (getFrameIntervalMs [this ] (get @params :interval-ms))
      (getMinZStepUm [this ] (get @params :min-z-step-um))
      (getNumFrames [this ] (get @params :frames))
      (getPositionMode [this] (get @params :position-mode))
      (getRootName [this] (get @params :root-name))
      (getSaveFiles [this] (get @params :save-files))
      (getSliceMode [this] (get @params :slice-mode))
      (getSliceZBottomUm [this] (get @params :slice-bottom-um))
      (getSliceZStepUm [this] (get @params :slice-step-um))
      (getVerboseSummary [this] (compute-verbose-summary params))
      (getZTopUm [this] (get @params :slice-top-um))
      (installAutofocusPlugin [this p] nil)
      (isAcquisitionRunning [this] (@current-state :running))
      (isAutoFocusEnabled [this] (get @params :use-autofocus))
      (isChannelsSettingEnabled [this] (get @params :use-channels))
      (isConfigAvailable [this config] false)
      (isFramesSettingEnabled [this] (get @params :use-frames))
      (isMultiFieldRunning [this] (get @params :multi-field))
      (isMultiPositionEnabled [this] (get @params :use-positions))
      (isPaused [this] (@current-state :paused))
      (isShutterOpenForChannels [this ] (get @params :keep-shutter-open-channels))
      (isShutterOpenForStack [this ] (get @params :keep-shutter-open-slices))
      (isZSliceSettingEnabled [this ] (get @params :use-slices))
      (keepShutterOpenForChannels [this state] (set-param! :keep-shutter-open-channels state))
      (keepShutterOpenForStack [this state] (set-param! :keep-shutter-open-slices state))
      (removeImageProcessor [this p] nil)
      (restoreSystem [this] nil)
      (setAfSkipInterval [this state] (set-param! :autofocus-skip-interval state))
      (setCameraConfig [this config] nil)
      (setChannel [this row chan] (swap! params assoc-in [:channels row] chan))
      (setChannelGroup [this grp] (set-param! :channel-group grp)) 
      (setChannels [this channels] (set-param! :channels channels))
      (setComment [this comment] (set-param! :comment comment))
      (setCore [this core af-mgr] (set-param! :core core) (set-param! :af-mgr af-mgr))
      (setDirName [this name] (set-param! :dir-name name))
      (setDisplayMode [this mode] (set-param! :display-mode mode))
      (setFinished [this] (alter current-state assoc :finished true))
      (setFrames [this n interval] (set-param! :frames n) (set-param! :interval-ms interval))
      (setParameterPreferences [this prefs] (set-param! :prefs prefs))
      (setParentGUI [this gui] nil)
      (setPause [this state] (alter current-state assoc :paused state))
      (setPositionList [this pos-list] (set-param! :position-list pos-list))
      (setPositionMode [this mode] (set-param! :position-mode mode))
      (setRootName [this name] (set-param! :root-name name))
      (setSaveFiles [this state] (set-param! :save-files state))
      (setSingleFrame [this state] (set-param! :single-frame state))
      (setSingleWindow [this state] (set-param! :single-window state))
      (setSliceMode [this mode] (set-param! :slice-mode mode))
      (setSlices [this bot top step absolute] (swap! params assoc :slice-bottom-um bot :slice-top-um top :slice-step-um step :slice-absolute absolute))
      (setUpdateLiveWindow [this state] (set-param! :update-live-window state))
      (setZStageDevice [this focus-device] (set-param! :focus-device focus-device))
      (shutdown [this] nil)
      (stop [this interrupted] (alter current-state assoc :interrupted interrupted)))))

   

   

   
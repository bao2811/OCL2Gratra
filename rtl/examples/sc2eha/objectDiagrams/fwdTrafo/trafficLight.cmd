-----------------------------------------------------------------------------
!create rc: RuleCollection 
------------------------------------------------------- INIT - OFF
!create sc1 : Statechart
!create ss1 : SimpState
!create t1 : Trans
!create init1 : Init
-- associations
!insert (t1, init1) into TransFrom
!insert (sc1, init1) into OwnsState
!insert (sc1, ss1) into OwnsState
!insert (sc1, t1) into OwnsTrans
!insert (t1, ss1) into TransTo
-- attributes
!set init1.name := ''
!set ss1.name := 'OFF'
------------------------------------------------------- ON
!create cs1 : CompState
!create cs2 : CompState
!create cs3 : CompState
-- association
!insert (sc1, cs1) into OwnsState
!insert (sc1, cs2) into OwnsState
!insert (sc1, cs3) into OwnsState
!insert (cs1, cs2) into ContainsState
!insert (cs1, cs3) into ContainsState
-- attributes
!set cs1.name := 'On'
!set cs2.name := 'Lamp'
!set cs3.name := 'Camera'
!set cs1.isConcurr := true
!set cs2.isConcurr := false
!set cs3.isConcurr := false
------------------------------------------------------- Lamp
!create ss2 : SimpState
!create initLamp : Init
!create transLamp : Trans
-- associations
!insert (sc1, ss2) into OwnsState
!insert (cs2, ss2) into ContainsState
!insert (sc1, transLamp) into OwnsTrans
!insert (transLamp, ss2) into TransTo
!insert (transLamp, initLamp) into TransFrom
-- attributes
!set initLamp.name := ''
!set ss2.name := 'Green'
------------------------------------------------------- Camera
!create ssCameraOff : SimpState
!create ssCameraOn : SimpState
!create initCamera : Init
!create transCam : Trans
!create transCamSwitch1 : Trans
!create transCamSwitch2 : Trans
!create cameraSwitch1 : Event
!create cameraSwitch2 : Event
-- associations
!insert (transCamSwitch1, cameraSwitch1) into Trigger
!insert (transCamSwitch2, cameraSwitch2) into Trigger
!insert (sc1, ssCameraOn) into OwnsState
!insert (cs3, ssCameraOn) into ContainsState
!insert (sc1, ssCameraOff) into OwnsState
!insert (cs3, ssCameraOff) into ContainsState
!insert (sc1, transCam) into OwnsTrans
!insert (sc1, transCamSwitch1) into OwnsTrans
!insert (sc1, transCamSwitch2) into OwnsTrans
!insert (transCam, ssCameraOff) into TransTo
!insert (transCam, initCamera) into TransFrom
!insert (transCamSwitch1, ssCameraOff) into TransTo
!insert (transCamSwitch1, ssCameraOn) into TransFrom
!insert (transCamSwitch2, ssCameraOn) into TransTo
!insert (transCamSwitch2, ssCameraOff) into TransFrom
-- attributes
!set cameraSwitch1.name := 'CameraSwitch'
!set cameraSwitch2.name := 'CameraSwitch'
!set initCamera.name := ''
!set ssCameraOff.name := 'CameraOff'
!set ssCameraOn.name := 'CameraOn'
------------------------------------------------------- Off_2_On
!create switch1:Event
!create transOff2On : Trans
!insert (transOff2On, switch1) into Trigger
!insert (transOff2On, ss1) into TransFrom
!insert (transOff2On, cs1) into TransTo
!set switch1.name := 'Switch'
------------------------------------------------------- Yellow
!create yellow : SimpState
!set yellow.name := 'Yellow'
!insert (sc1, yellow) into OwnsState
------------------------------------------------------- TimeGreen
!create timeGreen:Event
!create transTimeGreen : Trans
!insert (transTimeGreen, timeGreen) into Trigger
!insert (transTimeGreen, yellow) into TransTo
!insert (transTimeGreen, ss2) into TransFrom
!set timeGreen.name := 'TimeGreen'
------------------------------------------------------- Red
!create red : CompState
!create transTimeYellow : Trans
!create timeYellow : Event
!set timeYellow.name := 'TimeYellow'
!set red.name := 'Red'
!insert (sc1, red) into OwnsState
!insert (cs2, red) into ContainsState
!insert (transTimeYellow, red) into TransTo
!insert (transTimeYellow, yellow) into TransFrom
!insert (transTimeYellow, timeYellow) into Trigger
------------------------------------------------------- Counter 0
!create count0 : SimpState
!create initRed : Init
!create transRed : Trans
-- associations
!insert (sc1, count0) into OwnsState
!insert (red, count0) into ContainsState
!insert (sc1, transRed) into OwnsTrans
!insert (transRed, count0) into TransTo
!insert (transRed, initRed) into TransFrom
-- attributes
!set initRed.name := ''
!set count0.name := 'Count0'
------------------------------------------------------- Counter 1
!create count1 : SimpState
!create transCarStop1 : Trans
!create carStop1 : Event
-- associations
!insert (sc1, count1) into OwnsState
!insert (red, count1) into ContainsState
!insert (sc1, transCarStop1) into OwnsTrans
!insert (transCarStop1, count1) into TransTo
!insert (transCarStop1, count0) into TransFrom
!insert (transCarStop1, carStop1) into Trigger
-- attributes
!set carStop1.name := 'CarStop'
!set count1.name := 'Count1'
------------------------------------------------------- Counter 2
!create count2 : SimpState
!create transCarStop2 : Trans
!create carStop2 : Event
-- associations
!insert (sc1, count2) into OwnsState
!insert (red, count2) into ContainsState
!insert (sc1, transCarStop2) into OwnsTrans
!insert (transCarStop2, count2) into TransTo
!insert (transCarStop2, count1) into TransFrom
!insert (transCarStop2, carStop2) into Trigger
-- attributes
!set carStop2.name := 'CarStop'
!set count2.name := 'Count2'
------------------------------------------------------- Red Yellow
!create redYellow : SimpState
!create transCarStop3 : Trans
!create carStop3 : Event
!create transTimeRed : Trans
!create timeRed : Event
-- associations
!insert (sc1, redYellow) into OwnsState
!insert (cs2, redYellow) into ContainsState
!insert (sc1, transCarStop3) into OwnsTrans
!insert (transCarStop3, redYellow) into TransTo
!insert (transCarStop3, count2) into TransFrom
!insert (sc1, transTimeRed) into OwnsTrans
!insert (transTimeRed, redYellow) into TransTo
!insert (transTimeRed, red) into TransFrom
!insert (transCarStop3, carStop3) into Trigger
!insert (transTimeRed, timeRed) into Trigger
-- attributes
!set timeRed.name := 'TimeRed'
!set carStop3.name := 'CarStop'
!set redYellow.name := 'Red Yellow'
------------------------------------------------------- Time Red Yellow
!create transTimeRedYellow : Trans
!create timeRedYellow : Event
!insert (transTimeRedYellow, timeRedYellow) into Trigger
!insert (transTimeRedYellow, ss2) into TransTo
!insert (transTimeRedYellow, redYellow) into TransFrom
!set timeRedYellow.name := 'TimeRedYellow'
------------------------------------------------------- On to OFF
!create transOn2Off : Trans
!create switch2 : Event
!insert (transOn2Off, switch2) into Trigger
!insert (sc1, transOn2Off) into OwnsTrans
!insert (transOn2Off, ss1) into TransTo
!insert (transOn2Off, cs1) into TransFrom
!set switch2.name := 'Switch'
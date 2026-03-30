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
-----------------------------------------------------------------------------
!create rc: RuleCollection 
-------------------------------------------------------StateChart
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

-------------------------------------------------------EHA
------------------------------------------------------- INIT - OFF
!create initH1 : InitH
!create aut1 : AutH
!create initH2 : InitH
!create eha1 : EHA
!insert (initH1, aut1) into RefiningAutH
!insert (aut1, initH2) into ContainsStateH
!insert (eha1, initH1) into InitState
!insert (eha1, aut1) into OwnsAutH
!set initH1.name := ''
!set initH2.name := 'OFF'
!set aut1.name := ''
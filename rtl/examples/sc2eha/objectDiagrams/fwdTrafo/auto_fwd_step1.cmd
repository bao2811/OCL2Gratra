-----------------------------------------------------------------------------
--open /home/duchanh/DuLieu_hanhdd/writting/doc/sefm09_ocl4tgg/implementation/sc2eha.use
!create rc: RuleCollection 
-------------------------------------------------------Example Statechart
!create sc:Statechart

!create onL_State:CompState
!set onL_State.name:='OnL'
!set onL_State.isConcurr:=true

!create on_State:CompState
!set on_State.name:='On'
!set on_State.isConcurr:=false

!insert (sc,on_State) into OwnsState
!insert (sc,onL_State) into OwnsState

!insert (onL_State,on_State) into ContainsState
-----------------------------------------------------
!create red_State:CompState
!set red_State.name:='Red'
!set red_State.isConcurr:=false
!insert (sc,red_State) into OwnsState
!insert (onL_State,red_State) into ContainsState
-------------------------------------------------------Example EHA Model
!create eha:EHA
!create onL_Aut:AutH
!set onL_Aut.name:='OnL'
!insert (eha,onL_Aut) into OwnsAutH
-------------------------------------------------------Corr
!create s2e:SC2EHA
!insert (s2e,eha) into R_EHA_SC2EHA
!insert (s2e,sc) into L_Statechart_SC2EHA
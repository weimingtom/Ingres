##      -- template file: inmdammn.tf  (Master/detail AppendMode menuitem)

'AppendMode' (VALIDATE = 0, ACTIVATE = 0,
        EXPLANATION = 'Display submenu for Appending new data'),
        KEY FRSKEY11 (VALIDATE = 0, ACTIVATE = 0) =
BEGIN
    IIretval = 1;
    SET_FORMS FORM (MODE = 'UPDATE');

    DISPLAY SUBMENU
    BEGIN
##  -- repeat on-timeout and on-dbevent escape code in the submenu
##  INCLUDE intimout.tf
##  INCLUDE indbevnt.tf
##
##  GENERATE USER_MENUITEMS
##
##  DEFINE $_loopa 'IIloop9'
##  DEFINE $_loopb 'IIloop10'
##  INCLUDE inmdsnmn.tf         -- master/detail AddNew menuitem.
##
##  INCLUDE inirmn.tf           -- RowInsert, RowDelete menuitems.
##
##  INCLUDE inclear.tf          -- Clear menuitem
##
##  INCLUDE inlookup.tf         -- ListChoices menuitem.
##
##  INCLUDE intblfnd.tf         -- menu commands: 'Find', 'Top' & 'Bottom'. 

    'Help' (VALIDATE = 0, ACTIVATE = 0,
        EXPLANATION = 'Display help for this frame'),
        KEY FRSKEY1 (VALIDATE = 0, ACTIVATE = 0) =
    BEGIN
##      GENERATE HELP fgmdupda.hlp
    END

    'End' (VALIDATE = 0, ACTIVATE = 0,
##  IF $user_qualified_query THEN   -- End takes you back to main menu.
        EXPLANATION = 'Leave AppendMode and re-enter Query mode'),
##  ELSE                            -- End takes you back to calling frame
        EXPLANATION = 'Return to previous frame'),
##  ENDIF
        KEY FRSKEY3 (VALIDATE = 0, ACTIVATE = 0) =
    BEGIN
##      IF $user_qualified_query THEN   -- End takes you back to main menu
##
        INQUIRE_FORMS FORM (IIint = CHANGE);
        IF (IIint = 1) THEN
            IIchar1 =
                PROMPT 'Do you wish to leave AppendMode without saving (y/n)?';
            IF (UPPERCASE(:IIchar1) != 'Y') THEN
                MESSAGE 'Select menuitem "AddNew" to save.';
                SLEEP 2;
                RESUME MENU;
            ENDIF;
        ENDIF;
        ENDLOOP;
##
##      ELSE            -- End takes you back to calling frame
##
##      INCLUDE inend.tf     -- check if want to save changes before leave.
##      GENERATE USER_ESCAPE FORM_END
	SET_FORMS FRS (TIMEOUT = IItimeout); /* Restore saved timeout value */
        RETURN $default_return_value;
##
##      ENDIF
    END
##
##  GENERATE USER_ESCAPE BEFORE_FIELD_ACTIVATES
##  GENERATE USER_ESCAPE AFTER_FIELD_ACTIVATES  -- Field-Change & Field-Exit
##
    END;

##  IF $user_qualified_query THEN
##
    SET_FORMS FORM (MODE = 'QUERY');
    CLEAR FIELD ALL;
##
##  ELSE
##
##  GENERATE USER_ESCAPE FORM_END
    SET_FORMS FRS (TIMEOUT = IItimeout); /* Restore saved timeout value */
    RETURN $default_return_value;
##
##  ENDIF
##
END

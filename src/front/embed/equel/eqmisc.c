/*
**	Copyright (c) 2004 Ingres Corporation
*/
# include <compat.h>
# include <si.h>
# include <eqsym.h>
# include <eqgen.h>
# include <eqrun.h>
# include <equel.h>
# include <eqesql.h>


/*
** Name: eqmisc.c - Miscellaneous routines called by ESQL/EQUEL shared routines.
**
** Defines:
**
**    esq_sqmods	- Generate IIsqMods routine to set runtime flags.
**    esq_eoscall	- Generate IIsqMods calls (only when needed) 
**			  for checking the EOS C string at runtime.
**    esq_geneos	- Checks to see if we have turned EOS checking on.
**			  If so, we need to generate negative lengths.
**
** History:
**    22-jun-1993 (kathryn)
**	Written.
**	21-jan-1999 (hanch04)
**	    replace nat and longnat with i4
**	31-aug-2000 (hanch04)
**	    cross change to main
**	    replace nat and longnat with i4
*/

/* Toggle to see if the EOS runtime checking is on/off */
static i4	eoscheck_on = FALSE;	/*  Initialize EOS checking to OFF */

/*
+* Name:    esq_sqmods - Generate call to IIsqMods().
**
** Description: Generate call to IIsqMods().
**
** Inputs:
**      mod_flag        - valid values defined in eqrun.h
**
**              IImodSINGLE     1               -- Singleton SELECT
**              IImodWITH       2               -- CONNECT with WITH clause
**              IImodDYNSEL     3               -- EXEC IMMED SELECT
**              IImodNAMES      4               -- Get names in descriptors
**              IImodCPAD       5               -- Ansi CHR blank-pad & EOS
**              IImodNOCPAD     6               -- Ingres CHR_TYPE EOS only
**		IImodCEOS	7		-- ANSI check C string for EOS.
**		IImodNOCEOS	8		-- Ingres don't check for EOS.
** Outputs:
**    Returns:
-*        void
**    Errors: none.
**
** History:
**    07-jun-1993 (kathryn)
**         Change to accept a parameter so that all IIsqMods() calls may be
**         generated by this routine. Previously it only generated IIsqMods(1).
**    22-jun-1993 (kathryn)
**	   Moved from esqmisc.c to this file.  This routine is only generated
**	   in ESQL, but is linked with EQUEL because it is called from 
**	   routines shared by ESQL/EQUEL (ret_close(), etc...).
**    21-dec-1993 (teresal)
**	   Updated for adding EOS C string checking. Set toggle if we are
**	   turning EOS checking on/off.
*/

void
esq_sqmods(i4 mod_flag)
{
   arg_push();
   arg_int_add( mod_flag);
   gen_call( IISQMODS );

   /* Set EOS checking toggle */
   if (mod_flag == IImodCEOS)
      eoscheck_on = TRUE;
   if (mod_flag == IImodNOCEOS)
      eoscheck_on = FALSE;
}

/*
* Name:    esq_eoscall - Generate calls for setting EOS checking on/off.
**
** Description: This routine eliminates excessive generation of IIsqMods()
**		calls which controls the runtime behavior for the "-check_eos"
**		preprocessor flag. IIsqMods(IImodCEOS) is generated before
**		the following LIBQ calls: IIputdomio, IIvarstrio, and
**		IIwritio (only when a string variable is being passed).
**		We call esq_eoscall() to avoid generating an IIsqMods() 
**		call before every IIputdomio, like so:
**
**			IIsqMods((int)7);
**			IIputdomio((short *)0,(int)1,(int)32,(int)-15,cvar1);
**			IIsqMods((int)7);
**			IIputdomio((short *)0,(int)1,(int)32,(int)-15,cvar2);
**
**		Instead we will generate the code like the following:
**
**			IIsqMods((int)7);	! Turn EOS checking on
**			IIputdomio((short *)0,(int)1,(int)32,(int)-15,cvar1);
**			IIputdomio((short *)0,(int)1,(int)32,(int)-15,cvar2);
**			...
**			IIsqMods((int)8);	! Turn EOS checking off
**
** Inputs:
**      mod_flag        - valid values defined in eqrun.h
**
**		IImodCEOS	7		-- ANSI check C string for EOS.
**		IImodNOCEOS	8		-- Ingres don't check for EOS.
** Outputs:
**    Returns:
-*        void
**    Errors: none.
**
** History:
**    22-dec-1993 (teresal)
**	   Written. Fixes bug 55915.
*/

void
esq_eoscall(i4 mod_flag)
{
   if ((mod_flag == IImodCEOS) && !eoscheck_on) 
	esq_sqmods(mod_flag);
   else if ((mod_flag == IImodNOCEOS) && eoscheck_on)
	esq_sqmods(mod_flag);
}

/*
* Name:    esq_geneos - Checks to see if we have turned EOS checking on.
**
** Description: This routine is used to determine if we need to generate
**		negative lengths to indicate the length of the CHAR
**		variable at declaraction time. This routine is called
**		by the C code generator. This is an extra fail-safe check
**		so we are not stuck with negative lengths that we
**		don't know what to do with at runtime.
** Inputs:
**	None.
** Outputs:
**	None.
**
**    Returns:
**	STATUS
**		OK 	- EOS is turned on.
**		FAIL 	- EOS checking is off - don't generate negative lengths.
** History:
**    22-dec-1993 (teresal)
**	   Written. Fixes bug 55915.
*/

STATUS
esq_geneos()
{
   if ((eq->eq_flags & EQ_CHREOS) && eoscheck_on) 
	return OK;
   return FAIL;
}

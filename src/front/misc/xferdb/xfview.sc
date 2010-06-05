/*
**	Copyright (c) 2004 Ingres Corporation
*/

# include	<compat.h>
# include	<gl.h>
# include	<sl.h>
# include	<iicommon.h>
# include	<st.h>
# include	<er.h>
# include       <si.h>
# include       <lo.h>
# include	<fe.h>
# include	<ug.h>
# include       <ui.h>
# include       <uigdata.h>
# include       <adf.h>
# include       <afe.h>
EXEC SQL INCLUDE <xf.sh>;
# include	"erxf.h"

/*
** Fool MING, which doesn't know about EXEC SQL INCLUDE
# include <xf.qsh>
*/

/**
** Name:	xfview.sc - write statement to create view.
**
** Description:
**	This file defines:
**
**	xfviews		write statements to create views.
**
** History:
**	13-jul-87 (rdesmond) written.
**	10-mar-88 (rdesmond)
**		removed trim() from target list for 'text' field.
**	21-apr-89 (marian)
**		Pass in is_distrib to set the right range variable
**		for STAR.
**	27-may-89 (marian)
**		change is_distrib to global with_distrib.
**	05-mar-1990 (mgw)
**		Changed #include <erxf.h> to #include "erxf.h" since this is
**		a local file and some platforms need to make the destinction.
**	04-may-90 (billc)
**		Major rewrite.  Convert to SQL.
**	03-sep-91 (billc)
**		Fix 39609 - was putting "\sqlcreate view ..." in STAR script.
**	04-mar-92 (billc)
**		Major rewrite for FIPS.
**      27-jul-1992 (billc)
**              Rename from .qsc to .sc suffix.
**      28-oct-94 (sarjo01) Bug 63640
**              xfviews(): added order by iitable.table_reltid to get views
**              back in same order they were created. Fixes problem of view-
**              on-view created out of sequence.
**	31-jan-95 (wonst02) Bug #66496
**		This bug was caused by an incorrect cross-integration of main
**		to ca11sol.  Used change #415078 as my basis.
**	03-feb-95 (wonst02) Bug #63766
**		For fips, look for both $ingres and $INGRES
**	14-may-96 (kch)
**		We now ignore grant 'copy_into' and 'copy_from' privileges.
**		This change fixes bug 74525.
**	07-oct-1997 (nanpr01)
**		Fix bug 86131, 63640
**		Creating view in wrong order.
**      11-Dec-97 (nicph02) Bug 87622
**              We now check that the object type in iipermits is 'V'
**              when retrieving the grant statement for the view.
**      26-Apr-1999 (carsu07)
**            The copy.in script generated by copydb and unloaddb does not
**            contain create view statements for views owned by $ingres.     
**            This fix will now generate create view statements for non
**            system catalog views (e.g. ima views) owned by $ingres (Bug 94930)
**	21-jan-1999 (hanch04)
**	    replace nat and longnat with i4
**	31-aug-2000 (hanch04)
**	    cross change to main
**	    replace nat and longnat with i4
**      04-jan-2002 (stial01)
**          Implement -add_drop for -with_views, -with_proc, -with_rules
**	23-oct-2002 (gupsh01)
**	    Corrected the orders in which views are created, we now
**	    sort by reltid first also for non 6.5 catalogs.
**      19-dec-2002 (stial01)
**          Don't generate drop procedure statements for system generated procs
**	29-jan-03 (inkdo01)
**	    Added drop sequence procedure for sequence support.
**      12-feb-03 (stial01)
**          Only generate one drop procedure statement per procedure
**	9-Sep-2004 (schka24)
**	    Generate dbevent permit drops for 6.4 upgradedb when asked.
**	    Avoid spurious set-authorization noise by blank-trimming ID's.
**	20-Sep-2004 (schka24)
**	    Drop $ingres procs, but not ii-procs or system-use G procs,
**	    when drop requested (upgradedb).
**	02-Dec-2005 (gupsh01)
**	    Fixed xfdrop_sequences() routine to call the correct sql 
**	    statements for obtaining sequences.
**      13-Jun-2008 (coomi01) Bug 110708
**          Whenever copying view, should the check_option flag be
**          set to not used ('N'), squash any occurance of
**          "with check option" in query text.     
**      28-jan-2009 (stial01)
**          Use DB_MAXNAME for database objects.
**/

/* # define's */
/* GLOBALDEF's */
GLOBALREF bool With_sequences;
GLOBALREF bool With_comments;
/* extern's */

/* static's */
static void writeview( TXT_HANDLE	**tfdp,
	XF_TABINFO	*vi,
	char	*dml,
	char	*subtype,
	char	*permit_grantor,
	i2	permit_number,
	i4	text_sequence,
	i4	*viewcount,
	i4	*regcount,
	char	*text_segment,
	char    *check_option);

/*{
** Name:	xfviews - write statements to create views and set permits.
**
** Description:
**
** Inputs:
**
** Outputs:
**	numobjs		pointer to integer, so we can report the number of
**			views we found.
**
**	Returns:
**		none.
**
** History:
**	13-jul-87 (rdesmond) written.
**	10-mar-88 (rdesmond)
**		removed trim() from target list for 'text' field.
**	18-aug-88 (marian)
**		Changed retrieve statement to reflect column name changes in
**	18-aug-88 (marian)
**		Took out #ifdef HACKFOR50 since it is no longer needed.
**      28-oct-94 (sarjo01)
**              order views by iitables.table_reltid to get them in correct
**              order of creation.
**	31-jan-95 (wonst02) Bug #66496
**		This bug was caused by an incorrect cross-integration of main
**		to ca11sol.  Used change #415078 as my basis.
**	14-may-96 (kch)
**		We now ignore grant 'copy_into' and 'copy_from' privileges
**		when retrieving grant text from iipermits. This change fixes
**		bug 74525.
**	07-oct-1997 (nanpr01)
**		Fix bug 86131, 63640
**		Creating view in wrong order.
**      11-Dec-97 (nicph02) Bug 87622
**              We now check that the object type in iipermits is 'V'
**              when retrieving the grant statement for the view.
**		also for post 6.5 iipermits have a table_type = 'T' 
**		for views also so add that check for procedures. 
**	4-Jul-2008 (kibro01) b120333
**		Add call to generate comments within views.
**      13-Jun-2008 (coomi01) Bug 110708
**              Extend query to fetch check_option flag.
**              Before writing final view, ensure check_option 
**              is sqaushed if required.
**	16-Jul-2008 (kibro01) b120639
**		Fix ordering, broken by fix to b110708 above
**	13-May-2009 (gupsh01)
**		Only write comments if there are any views.
*/

void
xfviews(numobjs)
i4	*numobjs;
{
EXEC SQL BEGIN DECLARE SECTION;
    char    	text_segment[XF_VIEWLINE + 1];
    char        check_option[2];
    char    	dml[2];
    char    	subtype[2];
    char        alter_date[AFE_DATESIZE + 1];
    i4	    	text_sequence;
    i2	    	permit_number;
    i2	    	permit_depth;
    char	permit_grantor[DB_MAXNAME + 1];
    char        view_base[8];
    i4          stamp1;
    i4          stamp2;
    i4		reltid;
    auto XF_TABINFO	vi;
EXEC SQL END DECLARE SECTION;

    auto TXT_HANDLE	*tfd = NULL;
    i4		viewcount = 0;
    i4		regcount = 0;

    /*
    **  Default the check option flag for no further action
    */
    *check_option = 'Y';

    if (With_65_catalogs)
    {
	/* 1st clause gets view text. */
	EXEC SQL SELECT 
		    t.view_base ,t.table_relstamp1, t.table_relstamp2,
                    t.alter_date, t.table_owner, t.table_name, 
		    '', -1, -1,
		    v.text_sequence, v.text_segment,
		    v.check_option,
		    v.view_dml, t.table_type, t.table_subtype, t.table_reltid
	    INTO 
		    :view_base, :stamp1, :stamp2, 
                    :alter_date, :vi.owner, :vi.name, 
		    :permit_grantor, :permit_depth, :permit_number, 
		    :text_sequence, :text_segment,
		    :check_option,
		    :dml, :vi.table_type, :subtype, :reltid
		FROM iiviews v, iitables t
		WHERE v.table_name = t.table_name
		    AND v.table_owner = t.table_owner
		    AND (v.table_owner = :Owner OR '' = :Owner)
		    AND t.table_type = 'V'
		    AND  t.system_use = 'U'

	/* 
	** 2nd clause gets permit text from iipermits.
	** This is empty if it's a STAR database.
	*/
	UNION SELECT 
		    t.view_base, t.table_relstamp1, t.table_relstamp2,
                    t.alter_date, pe.object_owner, pe.object_name, 
		    pe.permit_grantor, pe.permit_depth, -- new columns in 6.5
		    pe.permit_number, pe.text_sequence,
		    pe.text_segment, '', '', '', '', t.table_reltid
		FROM iipermits pe, iitables t
		WHERE t.table_name = pe.object_name
		    AND t.table_owner = pe.object_owner
		    AND t.table_type = 'V'
                    AND pe.object_type = 'V'
		    AND (t.table_owner = :Owner OR '' = :Owner)
		    AND  t.system_use ='U'
		    AND pe.text_segment NOT LIKE '%copy_into%'
		    AND pe.text_segment NOT LIKE '%copy_from%'
	ORDER BY 1 DESC, 16, 4, 5, 8, 9, 10;
	EXEC SQL BEGIN;
	{
	    writeview( &tfd, &vi, dml, subtype,
		    permit_grantor, permit_number, 
		    text_sequence, &viewcount, &regcount, text_segment, check_option);
	}
	EXEC SQL END;
    }
    else
    {
	/*
	** Three-way union.  We could theoretically do two-way, but all-to-all
	** permissions make it very hard to handle all possible states.  So I 
	** added one special state, one per-view, that appears after the 
	** view text and returns the all-to-all info.  All-to-all info is
	** a no-op in 6.5.
	*/

	EXEC SQL SELECT t.alter_date, t.table_owner, t.table_name, '',
		    -1, v.text_sequence, v.text_segment, 
		    '', '', 
		    v.view_dml, t.table_type, t.table_subtype, t.table_reltid
	    INTO 
		    :alter_date, :vi.owner, :vi.name, :permit_grantor, 
		    :permit_number, :text_sequence, :text_segment,
		    :vi.alltoall, :vi.rettoall,
		    :dml, :vi.table_type, :subtype, :reltid
		FROM iiviews v, iitables t
		WHERE v.table_name = t.table_name
		    AND v.table_owner = t.table_owner
		    AND (v.table_owner = :Owner OR '' = :Owner)
 		    AND t.table_type = 'V'
                    AND  t.system_use = 'U'
	UNION SELECT 
		    t.alter_date, t.table_owner, t.table_name, '', 
		    0, 1, '', t.all_to_all, t.ret_to_all, 
		    '', t.table_type, t.table_subtype, t.table_reltid
		FROM iitables t
		WHERE t.table_type = 'V'
		    AND (t.table_owner = :Owner OR '' = :Owner)
		    AND  t.system_use = 'U'
	UNION SELECT t.alter_date, pe.object_owner, pe.object_name, 
		    pe.object_owner, -- owner is always grantor before 6.5
		    pe.permit_number, pe.text_sequence,
		    pe.text_segment, '', '', '', '', '',t.table_reltid
		FROM iipermits pe, iitables t
		WHERE t.table_name = pe.object_name
		    AND t.table_owner = pe.object_owner
		    AND t.table_type = 'V' 
		    AND (pe.object_type = 'V' OR pe.object_type = 'T') 
		    AND (t.table_owner = :Owner OR '' = :Owner)
		    AND  t.system_use = 'U' 
	ORDER BY 13, 1 ASC,  2, 3, 5, 6; 
	EXEC SQL BEGIN;
	{
	    writeview( &tfd, &vi, dml, subtype, 
		    permit_grantor, permit_number, 
		    text_sequence, &viewcount, &regcount, text_segment, check_option);

	}
	EXEC SQL END;
    }

    if (tfd != NULL)
    {
	if ( 'N' == *check_option )
	{
	    /* Squash any check option */
	    xfreplace(tfd, 1, 1, "with check option", "");	
	}
        xfclose(tfd);
    }

    *numobjs += viewcount;
    xf_found_msg(ERx("V"), viewcount);
    if (regcount > 0)
    {
	*numobjs += regcount;
	xf_found_msg(ERx("V*"), regcount);
    }
    if (viewcount && With_comments)
	xfcomments(&vi);
}


/*{
** Name:	writeview - write statements to create views and set permits.
**
** Description:
**
** Inputs:
**
** Outputs:
**
**	Returns:
** History:
**      13-Jun-2008 (coomi01) Bug 110708
**      Add new parameter, check_option.
**      Check and squash check_option before any flushes as required.
*/

static void
writeview(
    	TXT_HANDLE	**tfdp,
	XF_TABINFO	*vi,
	char	*dml,
	char	*subtype,
	char	*permit_grantor,
	i2	permit_number,
	i4	text_sequence,
	i4	*viewcount,
	i4	*regcount,
	char	*text_segment,
	char    *check_option
    )
{
    xfread_id(vi->name);
    if (!xfselected(vi->name)) 
	return; 

    xfread_id(vi->owner);

    if (permit_number == -1 && text_sequence == 1)
    {
	/*
	** 1st UNION clause -- New view definition.
	** This is the first line of definition text for a view.
	*/

	if (*subtype == 'N')
	    (*viewcount)++;
	else
	    (*regcount)++;

	if (*tfdp == NULL)
	{
	    /*
	    ** First time called.  Write informative comment, open text handle. 
	    */

	    xfwritehdr(VIEWS_COMMENT);
	    *tfdp = xfreopen(Xf_in, TH_IS_BUFFERED);
	}
	else
	{
	    if ( 'N' == *check_option )
	    {
		/* Squash any check option */
		xfreplace(*tfdp, 1, 1, "with check option", "");	
	    }
	    xfflush(*tfdp);
	}

	/* Does user id have to be reset? */
	xfsetauth(*tfdp, vi->owner);

	xfsetlang(*tfdp, *dml == 'Q' ? DB_QUEL : DB_SQL);
    }
    else if (permit_number == 0)
    {
	/* 
	** Pre-6.5, the second UNION clause.  This gives us the all-to-all
	** and ret-to-all permission info about the view. 
	** This clause goes away in 6.5.
	** 
	** This is a separate clause since we
	** have to handle all-to-all AFTER the entire view definition.)
	*/

	if (vi->alltoall[0] == 'Y' || vi->rettoall[0] == 'Y')
	{
	    if ( 'N' == *check_option )
	    {
		/* Squash any check option */
		xfreplace(*tfdp, 1, 1, "with check option", "");	
	    }
	    xfflush(*tfdp);
	    xfalltoall(vi);
	}
    }
    else if (permit_number > 0 && text_sequence == 1)
    {
	/* 
	** Permission text.  This is the first line of a permission on
	** a view that we've already seen.
	*/

	/* flush previous statement */
	if ( 'N' == *check_option )
	{
	    /* Squash any check option */
	    xfreplace(*tfdp, 1, 1, "with check option", "");	
	}
	xfflush(*tfdp);

	/* Do we need to reset the permit grantor's user id? */
	xfread_id(permit_grantor);
	xfsetauth(*tfdp, permit_grantor);

        /* set the DML that the permit is written in */
        if (STbcompare(text_segment, 6, ERx("create"), 6, FALSE) == 0
              || STbcompare(text_segment, 5, ERx("grant"), 5, FALSE) == 0)
        {
            xfsetlang(*tfdp, DB_SQL);
        }
        else
        {
            xfsetlang(*tfdp, DB_QUEL);
        }

    }

    xfwrite(*tfdp, text_segment);
}

xfdrop_sequences()
{
    EXEC SQL BEGIN DECLARE SECTION;
	char	seq_name[DB_MAXNAME + 1];
	char	seq_owner[DB_MAXNAME + 1];
    EXEC SQL END DECLARE SECTION;

    if (!With_sequences)
	return;

    EXEC SQL SELECT DISTINCT seq_name, seq_owner
    INTO :seq_name, :seq_owner
    FROM iisequences;

    EXEC SQL BEGIN;
    {
	xfread_id(&seq_name[0]);
	xfread_id(&seq_owner[0]);
	xfsetauth(Xf_in, seq_owner);
	xfwrite(Xf_in, ERx("drop sequence "));
	xfwrite_id(Xf_in, seq_name);
	xfwrite(Xf_in, GO_STMT);
    }
    EXEC SQL END;
}

void xfdrop_rules(i4 output_flags)
{
    EXEC SQL BEGIN DECLARE SECTION;
	char	rule_name[DB_MAXNAME + 1];
	char	rule_owner[DB_MAXNAME + 1];
    EXEC SQL END DECLARE SECTION;

    EXEC SQL SELECT DISTINCT rule_name, rule_owner
    INTO :rule_name, :rule_owner
    FROM iirules
    WHERE system_use <> 'G'
    ORDER BY rule_owner, rule_name;

    EXEC SQL BEGIN;
    {
	xfread_id(&rule_name[0]);
	xfread_id(&rule_owner[0]);
	xfsetauth(Xf_in, rule_owner);
	xfwrite(Xf_in, ERx("drop rule "));
	xfwrite_id(Xf_in, rule_name);
	xfwrite(Xf_in, GO_STMT);
    }
    EXEC SQL END;

    /* If we're dropping stuff, see if we need to drop dbevent permits.
    ** (dbevents are created before rules, in the rule handler, so why
    ** not continue the madness and put the dbevent permit drops here.
    ** How stupid is that?)
    ** We need to worry about dbevent permits because they existed
    ** back in 6.4, and old permits are to be reissued.
    */
    if ((output_flags & XF_DROP_INCLUDE) && (output_flags & XF_PERMITS_ONLY))
    {
	EXEC SQL SELECT DISTINCT event_name, event_owner
	    INTO :rule_name, :rule_owner
	    FROM iievents
	    ORDER BY event_owner, event_name;
	EXEC SQL BEGIN;
	    xfread_id(&rule_name[0]);
	    xfread_id(&rule_owner[0]);
	    xfsetauth(Xf_in, rule_owner);
	    xfwrite(Xf_in, ERx("drop permit on dbevent "));
	    xfwrite_id(Xf_in, rule_name);
	    xfwrite(Xf_in, " all");
	    xfwrite(Xf_in, GO_STMT);
	EXEC SQL END;
    }
}

xfdrop_procs()
{
    EXEC SQL BEGIN DECLARE SECTION;
	char	proc_name[DB_MAXNAME + 1];
	char	proc_owner[DB_MAXNAME + 1];
	char	proc_create_date[AFE_DATESIZE + 1];
    EXEC SQL END DECLARE SECTION;

    /*
    ** Ordering by create_date desc to TRY to drop procedures 
    ** in reverse create order...
    ** However create_date is not necessarily unique
    ** To guarantee reverse create order we would have to select
    ** from iiprocedure, order by dbp_id desc, but iiprocedure is not
    ** a standard catalog.
    ** Fortunately, the drops will work in any order.
    */
    EXEC SQL SELECT DISTINCT procedure_name, procedure_owner, create_date
    INTO :proc_name, :proc_owner, :proc_create_date
    FROM iiprocedures 
    WHERE LOWERCASE(procedure_name) NOT LIKE 'ii%'
    AND system_use <> 'G'
    ORDER BY create_date DESC;

    EXEC SQL BEGIN;
    {
	xfread_id(&proc_name[0]);
	xfread_id(&proc_owner[0]);
	xfsetauth(Xf_in, proc_owner);
	xfwrite(Xf_in, ERx("drop procedure "));
	xfwrite_id(Xf_in, proc_name);
	xfwrite(Xf_in, GO_STMT);
    }
    EXEC SQL END;
}

xfdrop_views()
{
    EXEC SQL BEGIN DECLARE SECTION;
	i4	table_reltid;
	char	table_name[DB_MAXNAME + 1];
	char	table_owner[DB_MAXNAME + 1];
    EXEC SQL END DECLARE SECTION;

    EXEC SQL SELECT table_reltid, table_name, table_owner
    INTO :table_reltid, :table_name, :table_owner
    FROM iitables 
    WHERE table_type = 'V'
    ORDER BY table_reltid DESC;

    EXEC SQL BEGIN;
    {
	if (!xf_is_cat(table_name))
	{
	    xfread_id(&table_name[0]);
	    xfread_id(&table_owner[0]);
	    xfsetauth(Xf_in, table_owner);
	    xfwrite(Xf_in, ERx("drop view "));
	    xfwrite_id(Xf_in, table_name);
	    xfwrite(Xf_in, GO_STMT);
	}
    }
    EXEC SQL END;
}
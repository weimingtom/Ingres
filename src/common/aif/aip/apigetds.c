/*
** Copyright (c) 2004 Ingres Corporation
*/

# include <compat.h>
# include <gl.h>
# include <iicommon.h>

# include <iiapi.h>
# include <api.h>
# include <apishndl.h>
# include <apiehndl.h>
# include <apierhnd.h>
# include <apidspth.h>
# include <apimisc.h>
# include <apitrace.h>

/*
** Name: apigetds.c
**
** Description:
**	This file contains the IIapi_getDescriptor() function interface.
**
**      IIapi_getDescriptor  API get descriptor function
**
** History:
**      01-oct-93 (ctham)
**          creation
**      17-may-94 (ctham)
**          change all prefix from CLI to IIAPI.
**      17-may-94 (ctham)
**          use II_EXPORT for functions which will cross the interface
**          between API and the application.
**	 31-may-94 (ctham)
**	     clean up error handle interface.
**	13-Dec-94 (gordy)
**	    Set output parms to safe values.
**	20-Dec-94 (gordy)
**	    Clean up common handle processing.
**	23-Jan-95 (gordy)
**	    Clean up error handling.
**	10-Feb-95 (gordy)
**	    Make callback for all errors.
**	 8-Mar-95 (gordy)
**	    Use IIapi_initialized() to test for init.
**	 9-Mar-95 (gordy)
**	    Silence compiler warnings.
**	19-May-95 (gordy)
**	    Fixed include statements.
**	28-Jul-95 (gordy)
**	    Fixed tracing of pointers.
**	17-Jan-96 (gordy)
**	    Added environment handles.
**	 2-Oct-96 (gordy)
**	    Replaced original SQL state machines which a generic
**	    interface to facilitate additional connection types.
**      31-May-05 (gordy)
**          Don't permit new requests on handles marked for deletion.
*/




/*
** Name: IIapi_getDescriptor
**
** Description:
**	This function provide an interface for the frontend application
**      to get descriptors of subsequent tuple data from the DBMS server.
**      Please refer to the API user specification for function description.
**
**      getDescrParm  input and output parameters for IIapi_getDescriptor()
**
** Return value:
**      none.
**
** Re-entrancy:
**	This function does not update shared memory.
**
** History:
**      01-oct-93 (ctham)
**          creation
**      17-may-94 (ctham)
**          change all prefix from CLI to IIAPI.
**      17-may-94 (ctham)
**          use II_EXPORT for functions which will cross the interface
**          between API and the application.
**	 31-may-94 (ctham)
**	     clean up error handle interface.
**	13-Dec-94 (gordy)
**	    Set output parms to safe values.
**	20-Dec-94 (gordy)
**	    Clean up common handle processing.
**	23-Jan-95 (gordy)
**	    Clean up error handling.
**	10-Feb-95 (gordy)
**	    Make callback for all errors.
**	 8-Mar-95 (gordy)
**	    Use IIapi_initialized() to test for init.
**	28-Jul-95 (gordy)
**	    Fixed tracing of pointers.
**      31-May-05 (gordy)
**          Don't permit new requests on handles marked for deletion.
*/

II_EXTERN II_VOID II_FAR II_EXPORT
IIapi_getDescriptor( IIAPI_GETDESCRPARM II_FAR *getDescrParm )
{
    IIAPI_HNDL	*handle;
    
    IIAPI_TRACE( IIAPI_TR_TRACE )
	( "IIapi_getDescriptor: retrieving columns from server\n" );
    
    /*
    ** Validate Input parameters
    */
    if ( ! getDescrParm )
    {
	IIAPI_TRACE( IIAPI_TR_ERROR )
	    ( "IIapi_getDescriptor: null getDescriptor parameters\n" );
	return;
    }
    
    getDescrParm->gd_genParm.gp_completed = FALSE;
    getDescrParm->gd_genParm.gp_status = IIAPI_ST_SUCCESS;
    getDescrParm->gd_genParm.gp_errorHandle = NULL;
    getDescrParm->gd_descriptorCount = 0;
    getDescrParm->gd_descriptor = NULL;
    handle = (IIAPI_HNDL *)getDescrParm->gd_stmtHandle;
    
    /*
    ** Make sure API is initialized
    */
    if ( ! IIapi_initialized() )
    {
	IIAPI_TRACE( IIAPI_TR_ERROR )
	    ( "IIapi_getDescriptor: API is not initialized\n" );
	IIapi_appCallback( &getDescrParm->gd_genParm, NULL, 
			   IIAPI_ST_NOT_INITIALIZED );
	return;
    }
    
    if ( ( ! IIapi_isStmtHndl( (IIAPI_STMTHNDL *)handle )  &&  
           ! IIapi_isDbevHndl( (IIAPI_DBEVHNDL *)handle ) )  ||
	 IIAPI_STALE_HANDLE( handle ) )
    {
	IIAPI_TRACE( IIAPI_TR_ERROR )
	    ( "IIapi_getDescriptor: invalid handle\n" );
	IIapi_appCallback( &getDescrParm->gd_genParm, NULL, 
			   IIAPI_ST_INVALID_HANDLE );
	return;
    }
    
    IIAPI_TRACE( IIAPI_TR_INFO )
	( "IIapi_getDescriptor: handle = %p\n", handle );
    
    IIapi_clearAllErrors( handle );
    IIapi_uiDispatch( IIAPI_EV_GETDESCR_FUNC, handle, (II_PTR)getDescrParm );

    return;
}

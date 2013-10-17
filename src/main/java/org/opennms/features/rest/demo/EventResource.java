package org.opennms.features.rest.demo;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.CriteriaBuilder;
import org.opennms.features.rest.demo.exception.NotFIQLOperatorException;
import org.opennms.features.rest.demo.util.QueryDecoder;
import org.opennms.netmgt.dao.api.EventDao;
import org.opennms.netmgt.dao.api.NodeDao;

import org.opennms.netmgt.model.OnmsEvent;
import org.opennms.netmgt.model.OnmsEventCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate3.HibernateQueryException;

@Path("/events")
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class EventResource {

    private NodeDao nodeDao;
    private EventDao eventDao;
    private static Logger logger = LoggerFactory.getLogger(NodeResource.class);    

    /**
     * get all events in the system
     * 
     * @return
     */
    public List<OnmsEvent> getEvents() {
        return eventDao.findAll();
    }

    /**
     * get the event identified by the path parameter eventId
     * 
     * @param eventId
     * @return
     */
    @GET
    @Path("{eventId}")
    public OnmsEvent getEventById(@PathParam("eventId") final Integer eventId) {
        return eventDao.get(eventId);
    }

    /**
     * search events using FIQL and access paginated results
     * 
     * @param queryString
     * @param limit
     * @param offset
     * @param orderBy
     * @param order
     * @return
     */
    @GET
    public Response searchEvents(@QueryParam("_s") String queryString, @QueryParam("limit") String limit, 
            @QueryParam("offset") String offset, @QueryParam("orderBy") String orderBy, @QueryParam("order") String order) {
        QueryDecoder eqd = new EventQueryDecoder();
        
        if (queryString == null) {
            queryString = "";
        }
        
        if (limit == null) {
            limit = "10";
        }

        if (offset == null) {
            offset = "0";
        }
        
        if (orderBy == null) {
            orderBy = "eventTime";
        } 
        
        if (order == null) {
            order = "asc";
        } 
        
        Criteria crit;
        try{
            crit = eqd.FIQLtoCriteria(queryString, Integer.parseInt(limit), Integer.parseInt(offset), orderBy, order);
        }
        catch(NotFIQLOperatorException e){    //in a case where user has specified an invalid FIQL operator
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   
        }
        catch(ParseException e){    //in a case where user has provided data in wrong format
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   
        }
        catch(NumberFormatException e){    //in a case where user has provided wrong data for query params
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   
        } 
        catch(Exception e){
            logger.error(e.getMessage(), e);    
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   //in case of an unidentified error caused
        }
        
        OnmsEventCollection result;
        
        try{
            result = new OnmsEventCollection(eventDao.findMatching(crit));
        }
        catch(HibernateQueryException e){    //in a case where user has requested a non existing data type
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   
        }
        catch(Exception e){
            logger.error(e.getMessage(), e);    
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   //in case of an unidentified error caused
        }
        
        if (result.isEmpty()) {         //result set is empty
            return Response.noContent().build();
        }
        return Response.ok().entity(result).build();
    }

    /**
     * method to initialize local variable nodeDao using blueprint
     * @param nodeDao
     */
    public void setNodeDao(NodeDao nodeDao) {
        this.nodeDao = nodeDao;
    }

    /**
     * method to initialize local variable eventDao using blueprint
     * @param eventDao
     */
    public void setEventDao(EventDao eventDao) {
        this.eventDao = eventDao;
    }
    
    /**
     * inner class to do the query decoding part of the search function 
     *
     */
    private class EventQueryDecoder extends QueryDecoder { //start of inner class
        
        /**
         * implemented abstract method from QueryDecoder class
         * in order to create the appropriate criteria object
         * 
         */
        protected CriteriaBuilder CreateCriteriaBuilder(){
            final CriteriaBuilder builder = new CriteriaBuilder(OnmsEvent.class);
            
            builder.orderBy("eventTime").asc();
            
            return builder;
        }
        
        /**
         * implemented abstract method from QueryDecoder class
         * For the given property name respective comparable object is created
         * ex - createTime -> java.util.Date
         * 
         * TODO - extend to provide validations
         * 
         * @param propertyName
         * @param compareValue
         * @return
         * @throws ParseException 
         */
        protected Object getCompareObject(String propertyName, String compareValue) throws ParseException {
            if (propertyName.equals("eventCreateTime") || propertyName.equals("eventTime") || propertyName.equals("eventAckTime")) {
                DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                try {
                    Date formattedDate = formatter.parse(compareValue);
                    return formattedDate;
                } catch (ParseException e) {
                    e.printStackTrace();
                    throw new ParseException("Please specify dates in format \"yyyy-MM-dd'T'HH:mm:ss\"", 0);
                }
            }
            else if (propertyName.equals("eventId") || propertyName.equals("eventSeverity")) {
                return Integer.parseInt(compareValue);
            }
            else if (propertyName.equals("nodeId")) {
                return nodeDao.get(Integer.parseInt(compareValue));
            }
            return compareValue;
        }
    }//end of inner class
    
}

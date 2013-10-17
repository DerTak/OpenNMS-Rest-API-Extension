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
import org.opennms.netmgt.dao.api.OutageDao;
import org.opennms.netmgt.model.OnmsOutage;
import org.opennms.netmgt.model.OnmsOutageCollection;
import org.opennms.netmgt.model.OnmsSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate3.HibernateQueryException;

@Path("/outages")
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class OutageResource {
    
    private OutageDao outageDao;
    private static Logger logger = LoggerFactory.getLogger(NodeResource.class);    

    /**
     * get all outages in the system
     * 
     * @return
     */
    public List<OnmsOutage> getOutages() {
        return outageDao.findAll();
    }

    /**
     * get the outage identified by the path parameter outage Id
     * 
     * @param outageId
     * @return
     */
    @GET
    @Path("{outageId}")
    public OnmsOutage getOutageById(@PathParam("outageId") final Integer outageId) {
        return outageDao.get(outageId);
    }
    
    /**
     * method to initialize local variable eventDao using blueprint
     * @param eventDao
     */
    public void setOutageDao(OutageDao outageDao) {
        this.outageDao = outageDao;
    }
    
    /**
     * search outage data using FIQL and get paginated results
     * 
     * @param queryString
     * @param limit
     * @param offset
     * @param orderBy
     * @param order
     * @return
     */
    @GET
    public Response searchOutages(@QueryParam("_s") String queryString, @QueryParam("limit") String limit, 
            @QueryParam("offset") String offset, @QueryParam("orderBy") String orderBy, @QueryParam("order") String order) {
            QueryDecoder oqd = new OutageQueryDecoder();

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
                orderBy = "id";
            } 
            
            if (order == null) {
                order = "desc";
            } 
                 
            Criteria crit;
        try{
            crit = oqd.FIQLtoCriteria(queryString, Integer.parseInt(limit), Integer.parseInt(offset), orderBy, order);
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
        OnmsOutageCollection result;
        try{
            result = new OnmsOutageCollection(outageDao.findMatching(crit));
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
     * inner class to do the query decoding part of the search function 
     *
     */
    private class OutageQueryDecoder extends QueryDecoder { //start of inner class
        
        /**
         * implemented abstract method from QueryDecoder class
         * in order to create the appropriate criteria object
         * 
         */
        protected CriteriaBuilder CreateCriteriaBuilder(){
            final CriteriaBuilder builder = new CriteriaBuilder(OnmsOutage.class);
            
            builder.orderBy("id").desc();
            
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
            if (propertyName.equals("ifLostService") || propertyName.equals("ifRegainedService") || propertyName.equals("suppressTime")) {
                DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                try {
                    Date formattedDate = formatter.parse(compareValue);
                    return formattedDate;
                } catch (ParseException e) {
                    e.printStackTrace();
                    throw new ParseException("Please specify dates in format \"yyyy-MM-dd'T'HH:mm:ss\"", 0);
                }
            }
            else if (propertyName.equals("id")) {
                return Integer.parseInt(compareValue);
            }
            return compareValue;
        }
    }//end of inner class
}

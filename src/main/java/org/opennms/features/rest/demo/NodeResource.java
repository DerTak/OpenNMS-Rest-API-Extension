package org.opennms.features.rest.demo;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.CriteriaBuilder;
import org.opennms.core.criteria.Order;
import org.opennms.core.criteria.Alias.JoinType;
import org.opennms.core.criteria.restrictions.Restriction;
import org.opennms.core.criteria.restrictions.Restrictions;
import org.opennms.features.rest.demo.exception.NotFIQLOperatorException;
import org.opennms.features.rest.demo.util.QueryDecoder;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.api.CategoryDao;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsNodeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.hibernate3.HibernateQueryException;

@Path("/nodes")
@Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
public class NodeResource{

    private NodeDao nodeDao;
    private CategoryDao categoryDao;
    private static Logger logger = LoggerFactory.getLogger(NodeResource.class);

    /**
     * method to initialize local variable nodeDao using blueprint
     * @param nodeDao
     */
    public void setNodeDao(NodeDao nodeDao) {
        this.nodeDao = nodeDao;
    }
    
    /**
     * method to initialize local variable categoryDao using blueprint
     * @param categoryDao
     */
    public void setCategoryDao(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }
        
    /**
     * get a list of all the nodes present in the system
     * @return List<OnmsNode>
     */
    public List<OnmsNode> getNodes() {
        return nodeDao.findAll();
    }

    /**
     * get a specified node's details
     * @param nodeId
     * @return OnmsNode
     */
    @GET
    @Path("{nodeId}")
    public Response getNode(@PathParam("nodeId") final String nodeId) {
        OnmsNode result = nodeDao.get(nodeId);
        if (result == null) {
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity("Please specify a valid node ID").build();
        }
        return Response.ok().entity(result).build();
    }

    @GET
    @Path("{nodeId}/ipinterfaces")
    public Response getNodeIPInterfaces(@PathParam("nodeId") final String nodeId) {
        Set<OnmsIpInterface> result = nodeDao.get(nodeId).getIpInterfaces();
        if (result == null) {
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity("Please specify a valid node ID").build();
        }
        OnmsIpInterface[] resultArray = new OnmsIpInterface[result.size()];
        result.toArray(resultArray);
        return Response.ok().entity(resultArray).build();
    }

    @GET
    @Path("{nodeId}/ipinterfaces/{ipAddress}")
    public Response getNodeIPInterfacesByIPAddress(@PathParam("nodeId") final String nodeId, @PathParam("ipAddress") final String ipAddress) {
        OnmsIpInterface result = nodeDao.get(nodeId).getIpInterfaceByIpAddress(ipAddress);
        if (result == null) {
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity("Please specify a valid node ID").build();
        }
        return Response.ok().entity(result).build();
    }
    
    @GET
    @Path("{nodeId}/ipinterfaces/{ipAddress}/services")
    public Response getNodeServicesByIPAddress(@PathParam("nodeId") final String nodeId, @PathParam("ipAddress") final String ipAddress) {
        Set<OnmsMonitoredService> result = nodeDao.get(nodeId).getIpInterfaceByIpAddress(ipAddress).getMonitoredServices();
        if (result == null) {
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity("Please specify a valid node ID").build();
        }
        OnmsIpInterface[] resultArray = new OnmsIpInterface[result.size()];
        result.toArray(resultArray);
        return Response.ok().entity(resultArray).build();
    }
    
    @GET
    @Path("{nodeId}/ipinterfaces/{ipAddress}/services/{serviceName}")
    public Response getNodeServiceByIPAddressByServiceName(@PathParam("nodeId") final String nodeId, @PathParam("ipAddress") final String ipAddress, 
            @PathParam("serviceName") final String serviceName) {
        OnmsMonitoredService result = nodeDao.get(nodeId).getIpInterfaceByIpAddress(ipAddress).getMonitoredServiceByServiceType(serviceName);
        if (result == null) {
            return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity("Please specify a valid node ID").build();
        }
        return Response.ok().entity(result).build();
    }
    
    /**
     * method to get nodes belonging to several specified categories
     * categories are specified as an array of strings
     * sample URL
     * "http://localhost:8980/opennms/rest2/nodes/categories?q=Servers&q=Routers&q=Switches&q=Production&q=Test&q=Development"
     * 
     * @param categories
     * @return
     */
    @GET
    @Path("/categories")
    public Response getNodesByCategories(@QueryParam("q") List<String> categories){
        try{    
            if (categories.isEmpty()){
                /*
                 * options to consider
                 * - 400 bad request will be returned with a list of available categories - implemented
                 * - 200 oK with a collection of all the nodes available
                 * - 400 bad request with a error message
                 */
                List<OnmsCategory> result = categoryDao.findAll();
                OnmsCategory[] resultArray = null;
                resultArray = result.toArray(new OnmsCategory[0]);
                return Response.status(Response.Status.BAD_REQUEST).entity(resultArray).build();
            }
            else {
                List<OnmsCategory> onmsCategories = new ArrayList<OnmsCategory>();
                for (String category : categories) {
                    onmsCategories.add(categoryDao.findByName(category));
                    if (onmsCategories.get(onmsCategories.size() - 1) == null){ // invalid category specified
                        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).
                                entity("Please specify a set of valid categories").build();
                    }
                }
                List<OnmsNode> result = nodeDao.findAllByCategoryList(onmsCategories);
                if (result.isEmpty()) {                                         //result set is empty
                    return Response.noContent().build();
                }
                OnmsNodeList resultNodeList = new OnmsNodeList(result);
                return Response.ok().entity(resultNodeList).build();
            }
        }catch(Exception e){    
            logger.error(e.getMessage(), e);
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();       //in case of a unidentified error caused
        }
    }
    
    /**
     * Method to retrieve all nodes belonging to a single category
     * sample URL
     * "http://localhost:8980/opennms/rest2/nodes/categories/Servers"
     * 
     * @param category
     * @return
     */
    @GET
    @Path("/categories/{category}")
    public Response getNodesByCategory(@PathParam("category") String category){
        //{category} == null case is handled by getNodesByCategories method
        //therefore no need to check it in this method
        try{    
            OnmsCategory onmsCategory = categoryDao.findByName(category);
            if (onmsCategory == null){                                      // invalid category specified
                return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity("Please specify a valid category").build();
            }
            List<OnmsNode> result = nodeDao.findByCategory(onmsCategory);
            if (result.isEmpty()) {                                         //result set is empty
                return Response.noContent().build();
            }
            OnmsNodeList resultNodeList = new OnmsNodeList(result);
            return Response.ok().entity(resultNodeList).build();
        }catch(Exception e){  
            logger.error(e.getMessage(), e);  
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   //in case of a unidentified error caused
        }
    }
    
    /**
     * Method added to validate empty foreign source string
     * @return
     */
    @GET
    @Path("/foreignSource")
    public Response getNodesByForeignSource(){
        //400 bad request
        return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity("Please specify a valid foreignSource").build();        
    }

    /**
     * Method to find all nodes belonging to a given foreign source name 
     * "http://localhost:8980/opennms/rest2/nodes/foreignSource/Servers"
     * 
     * @param category
     * @return
     */
    @GET
    @Path("/foreignSource/{foreignSource}")
    public Response getNodesByForeignSource(@PathParam("foreignSource") String foreignSource){
       //empty variable foreignSource is handled by overloaded method getNodesByForeignSource()
       try{
            List<OnmsNode> result = nodeDao.findByForeignSource(foreignSource);
            if (result.isEmpty()) {                                         //result set is empty
                return Response.noContent().build();
            }
            OnmsNodeList resultNodeList = new OnmsNodeList(result);
            return Response.ok().entity(resultNodeList).build();
        }catch(Exception e){
            logger.error(e.getMessage(), e);
            return Response.serverError().type(MediaType.TEXT_PLAIN).entity(e.getMessage()).build();   //in case of a unidentified error caused
        }
    }
    
    /**
     * quering node data using core.criteria
     * FIQL query is transmitted as a query parameter in the http request
     * 
     * example URLs - 
     * http://localhost:8980/opennms/rest2/nodes/search?_s=type==A
     * http://localhost:8980/opennms/rest2/nodes/search?_s=createTime==2013-01-01
     * http://localhost:8980/opennms/rest2/nodes/search?_s=createTime=gt=2013-06-14T20:41:45;(type==D,lastCapsdPoll=le=2013-12-30T00:00:00)
     * 
     * @param queryString
     * @return
     */
    @GET
    public Response searchNodes(@QueryParam("_s") String queryString, @QueryParam("limit") String limit, 
            @QueryParam("offset") String offset, @QueryParam("orderBy") String orderBy, @QueryParam("order") String order) {
        NodeQueryDecoder nqd = new NodeQueryDecoder();
        
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
            orderBy = "label";
        } 
        
        if (order == null) {
            order = "asc";
        } 
        
        Criteria crit;
        try{
            crit = nqd.FIQLtoCriteria(queryString, Integer.parseInt(limit), Integer.parseInt(offset), orderBy, order);
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
        
        OnmsNodeList result;
        
        try{
            result = new OnmsNodeList(nodeDao.findMatching(crit));
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
    private class NodeQueryDecoder extends QueryDecoder { //start of inner class
        
        /**
         * implemented abstract method from QueryDecoder class
         * in order to create the appropriate criteria object
         * 
         */
        protected CriteriaBuilder CreateCriteriaBuilder() {
            final CriteriaBuilder builder = new CriteriaBuilder(OnmsNode.class);
            builder.alias("snmpInterfaces", "snmpInterface", JoinType.LEFT_JOIN);
            builder.alias("ipInterfaces", "ipInterface", JoinType.LEFT_JOIN);
            builder.alias("categories", "category", JoinType.LEFT_JOIN);
    
            builder.orderBy("label").asc();
            
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
            if (propertyName.equals("createTime") || propertyName.equals("lastCapsdPoll")) {
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
            else if (propertyName.equals("categories")) {
                OnmsCategory onmsCategory = categoryDao.findByName(compareValue);
                if (onmsCategory == null){                                      // invalid category specified
                    throw new ParseException("Please specify a valid category instead of \"" + compareValue + "\"", 0);
                }
                return onmsCategory;
            }
            return compareValue;
        }

    }//end of inner class
    
    /**
     * method to test the criteria for node searching
     * based on the NodeRestService of old REST API
     * @return List<OnmsNode>
     */
    @GET
    @Path("/test")
    public List<OnmsNode> testSearch() {
        final CriteriaBuilder builder = new CriteriaBuilder(OnmsNode.class);
        builder.alias("snmpInterfaces", "snmpInterface", JoinType.LEFT_JOIN);
        builder.alias("ipInterfaces", "ipInterface", JoinType.LEFT_JOIN);
        builder.alias("categories", "category", JoinType.LEFT_JOIN);

        builder.orderBy("label").asc();
        
        final Criteria crit = builder.toCriteria();

        final List<Restriction> restrictions = new ArrayList<Restriction>(crit.getRestrictions());
//            restrictions.add(Restrictions.ne("type", "D"));
            List<OnmsCategory> onmsCategory = categoryDao.findAll();
            restrictions.add(Restrictions.eq("categories", onmsCategory.get(0)));
            crit.setRestrictions(restrictions);
            
            OnmsNodeList coll = null;
            
        try{
            coll = new OnmsNodeList(nodeDao.findMatching(crit));
        }catch(Exception e){
            System.out.println(e.getMessage());
        }
        
        crit.setLimit(null);
        crit.setOffset(null);
        crit.setOrders(new ArrayList<Order>());

        coll.setTotalCount(nodeDao.countMatching(crit));

        return coll;
    }
}

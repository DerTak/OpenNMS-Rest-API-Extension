package org.opennms.features.rest.demo.util;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.opennms.core.criteria.Criteria;
import org.opennms.core.criteria.CriteriaBuilder;
import org.opennms.core.criteria.restrictions.Restriction;
import org.opennms.core.criteria.restrictions.Restrictions;
import org.opennms.features.rest.demo.exception.MergeException;
import org.opennms.features.rest.demo.exception.NotFIQLOperatorException;

public abstract class QueryDecoder {

    /**
     * Application of template pattern to query decoder algorithm
     * main algorithmic flow to convert a given FIQL string to a matching criteria object
     * @param fiqlQuery
     * @return
     * @throws Exception
     */
    public Criteria FIQLtoCriteria(String fiqlQuery, int limit, int offset, String orderBy, String order) throws Exception{
        final CriteriaBuilder builder = CreateCriteriaBuilder();
        
        if (!orderBy.equals("")) {
            builder.clearOrder();
            if (order.equals("desc")) {
                builder.orderBy(orderBy).desc();
            } else {
                builder.orderBy(orderBy).asc();
            }
        }
                
        final Criteria crit = builder.toCriteria();
        
        crit.setLimit(limit);
        crit.setOffset(offset);
        
        if (fiqlQuery.equals("")) {
            return crit;
        }
        final List<Restriction> restrictions = new ArrayList<Restriction>(crit.getRestrictions());
        Restriction restriction = removeBrackets(fiqlQuery);
        restrictions.add(restriction);
        crit.setRestrictions(restrictions);
        
        return crit;
    }
    /**
     * Extended class should implement this method in order to
     * create criteriaBuilder object for a given data type to be queried
     * 
     * @return
     */
    protected abstract CriteriaBuilder CreateCriteriaBuilder();
    
    /**
     * create a Restriction object out of the queries containing brackets
     * 
     * @param bracketedQuery
     * @return
     * @throws Exception 
     */
    protected Restriction removeBrackets(String bracketedQuery) throws Exception {
        int newOpenBracket = bracketedQuery.indexOf("(");
                
        if (newOpenBracket != -1){ //if provided query contains an opening bracket
            
            int respectiveClosingBracket = newOpenBracket + extractBracketEndPoint(bracketedQuery.substring(newOpenBracket));
            
            //remove unnecessary brackets
            //LOGIC - If opening bracket and respective closing bracket are 1st and last character respectively remove those characters
            while (newOpenBracket == 0 && respectiveClosingBracket == bracketedQuery.length() - 1) {
                bracketedQuery = bracketedQuery.substring(newOpenBracket + 1, respectiveClosingBracket);
                newOpenBracket = bracketedQuery.indexOf("(");        
                if (newOpenBracket != -1){
                    respectiveClosingBracket = newOpenBracket + extractBracketEndPoint(bracketedQuery.substring(newOpenBracket));
                }
            }
            
            if (newOpenBracket == -1) {
                return createComplexRestriction(bracketedQuery);
            }
            
            if (respectiveClosingBracket == -1) {
                throw new ParseException("Respective closing bracket for the opening bracket at index " + newOpenBracket + " was not found", newOpenBracket);
            }
            
            //data structures to help completing the creation of complex queries
            List<Restriction> componentRestrictions = new ArrayList<Restriction>(); //to store created restrictions which are to be pivoted later
            List<Character> pivotPoints = new ArrayList<Character>(); //to store values of the pivot points
            //if restrictions.count = "n" then pivotPoints.count = "n - 1"
        
            if (newOpenBracket != 0) { //if new opening bracket is not the starting character
                Character pivotOperator = bracketedQuery.charAt(newOpenBracket - 1);
                if (pivotOperator == ',' || pivotOperator == ';') {
                    pivotPoints.add(pivotOperator);
                } else {
                    throw new ParseException("illeagle pivot operator at the location \"" + pivotOperator + "(\"", newOpenBracket - 1);
                }
                Restriction restriction = removeBrackets(bracketedQuery.substring(0, newOpenBracket - 1));
                componentRestrictions.add(restriction);                
            }
            //process bracketed part after the non bracketed part
            componentRestrictions.add(removeBrackets(bracketedQuery.substring(newOpenBracket + 1, respectiveClosingBracket)));
            
            if (respectiveClosingBracket != bracketedQuery.length() - 1){ //if query extends after the closing bracket
                Character pivotOperator = bracketedQuery.charAt(respectiveClosingBracket + 1);
                if (pivotOperator == ',' || pivotOperator == ';') {
                    pivotPoints.add(pivotOperator);
                } else {
                    throw new ParseException("illeagle pivot operator at the location \")" + pivotOperator + "\"", respectiveClosingBracket + 1);
                }
                componentRestrictions.add(removeBrackets(bracketedQuery.substring(respectiveClosingBracket + 2)));
            }
            
            return mergeBracketedRestrictions(componentRestrictions, pivotPoints);
        }
        else{
            return createComplexRestriction(bracketedQuery);
        }
    }
    
    /**
     * This method will return the index of the matching closing bracket for the provided string which starting with an opening bracket "("
     * 
     * @param processingQuery - a string starting with an opening bracket "("
     * @return index of respective closing bracket wrt starting "(" if found
     * else returns -1
     */
    private int extractBracketEndPoint(String processingQuery) {
        int bracketCounter = 0; //to get the location of respective closing bracket
        for (int i = 0; i < processingQuery.length(); i++) {//iterate over the given string 
            Character test = processingQuery.charAt(i);
            if (test == '(') {
                bracketCounter++;//for "(" increment bracketCounter
            } else if (test == ')') {
                bracketCounter--;//for ")" decrement bracketCounter
                if (bracketCounter == 0) { 
                    //return respective closing location 
                    return i;
                }
            }
        }
        return -1; //if matching bracket is not found
    }

    /**
     * method to merge restriction components disassembled in the process removing brackets
     * In the merging process AND is given priority over OR
     * 
     * @param componentRestrictions - disassembled Restrictions
     * @param pivotPoints - list of ";" and "," (AND, OR of FIQL)
     * @return
     * @throws MergeException 
     */
    private static Restriction mergeBracketedRestrictions(
                                                    List<Restriction> componentRestrictions,
                                                    List<Character> pivotPoints) throws MergeException {
        if (componentRestrictions.size() != pivotPoints.size() + 1) {
            throw new MergeException("There is a mis use of pivot operators (; & ,). Please recorrect query string and try again");
        }
        
        Restriction result = componentRestrictions.get(0);
        
        if (!pivotPoints.isEmpty()){ //If no pivot points exist
            List<Restriction> andMergedList = new ArrayList<Restriction>(); //data structure to help merge using AND operators
            andMergedList.add(result);
            int pivotIndex = 0;
            
            for (Character pivot : pivotPoints) {
                if (pivot == ';') {
                    //if AND pivot point is detected remove last element from andMergeList
                    //create new and-restriction with removed element and next component restriction
                    //add that element to andMergeList
                    result = Restrictions.and(
                                              andMergedList.remove(andMergedList.size() - 1), 
                                              componentRestrictions.get(pivotIndex + 1));
                    andMergedList.add(result);
                } else {
                    //else just add the next component restriction to andMergeList
                    andMergedList.add(componentRestrictions.get(pivotIndex + 1));
                }
                
                pivotIndex++; //increment pivot index to access next restriction element
            }
            
            result = andMergedList.get(0);
            
            if (andMergedList.size() > 1){//if andMergedList has more than 1 element
                //merge all elements with logical OR
                for (int i = 1; i < andMergedList.size(); i++) {
                    result = Restrictions.or(result, andMergedList.get(i));
                }
            }
        }
        
        return result;
    }

    /**
     * Method takes a complex query with AND (;) & OR (,)
     * and returns the matching restriction
     * 
     * @param complexQuery - query containing AND (;) & OR (,)
     * @return
     * @throws Exception 
     */
    private Restriction createComplexRestriction(String complexQuery) throws Exception {
        if (complexQuery.equals("")) {
            throw new ParseException("Please specify a not-null complex query", 0);
        }
        int strayClosingBracket = complexQuery.indexOf(")");
        if (strayClosingBracket != -1) {
            throw new ParseException("A closing bracket in the complex query " + complexQuery + " doesn't match any opening brackets", strayClosingBracket);
        }
        
        if (!complexQuery.contains(";") && !complexQuery.contains(",")) {
            return createPrimitiveRestriction(complexQuery);
        }
        String[] primitiveQueryStrings = complexQuery.split(",|;");
        
        List<Restriction> componentRestrictions = new ArrayList<Restriction>(); //to store created primitive restrictions which are to be pivoted later
        for (String primitiveQuery : primitiveQueryStrings) {
            componentRestrictions.add(createPrimitiveRestriction(primitiveQuery));
        }
        
        List<Character> pivotPoints = new ArrayList<Character>(); //to store values of the pivot points
        for (int i = 0; i < complexQuery.length(); i++) {//iterate over the given complexQuery string 
            Character test = complexQuery.charAt(i);
            if (test == ';' || test == ',') {
                pivotPoints.add(test);
            }
        }
        
        return mergeBracketedRestrictions(componentRestrictions, pivotPoints);
    }

    /**
     * method to create restrictions for the primitive operators
     * primitive operators = (==, !=, =lt=, =le=, =gt=, =ge=)
     * 
     * @param primitiveQuery
     * @return
     * @throws Exception 
     */
    private Restriction createPrimitiveRestriction(
                                            String primitiveQuery) throws Exception {
        if (primitiveQuery.equals("")) {
            throw new ParseException("Please specify a not-null primitive query", 0);
        }
        //pre-processing the string by split("=")
        String[] componentStrings = primitiveQuery.split("=");
                
        if (componentStrings.length == 2 && componentStrings[0].endsWith("!")) {//case "!="
            String propertyName = componentStrings[0].substring(0, componentStrings[0].length() - 1);
            if ("null".equalsIgnoreCase(componentStrings[1])) {
                return Restrictions.isNotNull(propertyName);
            } else if ("notNull".equalsIgnoreCase(componentStrings[1])) {
                return Restrictions.isNull(propertyName);
            }
            Object compareWith = getCompareObject(propertyName, componentStrings[1]);
            return Restrictions.ne(propertyName, compareWith);
        } 
        else if (componentStrings.length == 3) {
            
            if (componentStrings[1].equals("")) {//case "=="
                if ("null".equalsIgnoreCase(componentStrings[2])) {
                    return Restrictions.isNull(componentStrings[0]);
                } else if ("notNull".equalsIgnoreCase(componentStrings[2])) {
                    return Restrictions.isNotNull(componentStrings[0]);
                }
                Object compareWith = getCompareObject(componentStrings[0], componentStrings[2]);
                return Restrictions.eq(componentStrings[0], compareWith); 
            }
            
            Object compareWith = getCompareObject(componentStrings[0], componentStrings[2]);
            if (componentStrings[1].equals("lt")) {//case "=lt="
                return Restrictions.lt(componentStrings[0], compareWith); 
            } 
            else if (componentStrings[1].equals("le")) {//case "=le="
                return Restrictions.le(componentStrings[0], compareWith); 
            } 
            else if (componentStrings[1].equals("gt")) {//case "=gt="
                return Restrictions.gt(componentStrings[0], compareWith); 
            } 
            else if (componentStrings[1].equals("ge")) {//case "=ge="
                return Restrictions.ge(componentStrings[0], compareWith); 
            }
        }
        throw new NotFIQLOperatorException("operator used with query string \"" + primitiveQuery + 
                                           "\" is invalid. Please specify a valid operator.");
    }

    /**
     * For the given property name respective comparable object is created
     * other than creating the comparable object validation checks can be added
     * 
     * @param propertyName
     * @param compareValue
     * @return
     */
    protected abstract Object getCompareObject(String propertyName, String compareValue) throws Exception;

}

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>

<jsp:useBean id="maps" scope="request" type="java.util.List" />

<h1>Review</h1>

<p>
The Review menu contains a few different ways that you can look at your data.  
At this time, you can Review your Sightings by:
</p>

<c:set var="divWidth" value="48"></c:set>
<sec:authorize ifAnyGranted="ROLE_ADMIN,ROLE_SUPERVISOR,ROLE_POWER_USER,ROLE_USER">
    <c:set var="divWidth" value="100"></c:set>
    <div class="left" style="width: 48%; padding: 5px;">
        <a href="${pageContext.request.contextPath}/map/mySightings.htm">
            <h2>My Sightings</h2>
        </a>
        <p>This form shows you your own Sightings, and lets you filter it by a few simple parameters.
        <a href="${pageContext.request.contextPath}/map/mySightings.htm">Click here</a>
        </p>
        <a href="${pageContext.request.contextPath}/map/mySightings.htm">
            <img style="width: 100%;" src="${pageContext.request.contextPath}/images/bdrs/review/my_sightings.png">
        </a>
    </div>
</sec:authorize>
<div class="left" style="width: 48%; padding: 5px;">
    <a href="${pageContext.request.contextPath}/review/sightings/advancedReview.htm">
        <h2>Advanced Review</h2>
    </a>
    <p>This form lets you filter your Sightings and other users' public Sightings across a wider range of parameters.
    <a href="${pageContext.request.contextPath}/review/sightings/advancedReview.htm">Click here</a>
    </p>
    <a href="${pageContext.request.contextPath}/review/sightings/advancedReview.htm">
        <img style="width: 100%;" src="${pageContext.request.contextPath}/images/bdrs/review/advanced_review.png">
    </a>
</div>
<c:if test="<%= maps != null && !maps.isEmpty() %>">
    <div class="left" style="width: ${divWidth}%; padding: 5px;">
	    <h2>Map View</h2>
	    <p>This form shows a map with predefined sets of sightings on it and can be published publicly.  The maps you can view are:</p>
	    <ul>
	        <c:forEach items="${maps}" var="map">
	            <li>
	                <a href="${pageContext.request.contextPath}/bdrs/map/view.htm?geoMapId=${map.id}">${ map.name }</a>
	                <c:if test="${map.description != null}"> - ${map.description}</c:if>
	            </li>
	        </c:forEach>
	    </ul>
    </div>
</c:if>
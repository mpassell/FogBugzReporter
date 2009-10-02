/*
 * Copyright 2007-2008 Grove Hill Software, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.grovehillsoftware.reportbuilder

import java.text.DateFormat
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import groovy.text.SimpleTemplateEngine

class TimeSheetReportGenerator {
  String baseURL
  String logonToken
  Map<String, String> filterNameToIDMap
  SimpleTemplateEngine engine
  
  String logon(String baseURL, String email, String password) {
    this.baseURL = baseURL
    def logonURLString = "$baseURL/api.asp?cmd=logon&email=${email}&password=$password"
    def rootElem = new XmlSlurper().parse(logonURLString)
    logonToken = rootElem.token[0].text()
    filterNameToIDMap = buildFilterNameToIDMap()
    engine = new SimpleTemplateEngine()
    
    return logonToken
  }
  
  void outputReport(String filterName, Date startDate, Date endDate, String format,
  PrintWriter writer) {
    String initialFilterID = filterName != null ? getCurrentFilterID() : null
    
    Map<String, String> matchingBugIDs = setFilterAndFindMatches(filterName)
    
    // FogBugz spits out dates as Z/UTC/GMT
    TimeZone fogBugzTimeZone = TimeZone.getTimeZone('GMT')
    
    DateFormat fogBugzDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    fogBugzDateFormat.setTimeZone(fogBugzTimeZone)
    TimeInterval requestedInterval =
      roundToMidnightAndConvertTimeZone(startDate, endDate, fogBugzTimeZone)
    
    String searchStartString = fogBugzDateFormat.format(requestedInterval.start)
    String searchEndString = fogBugzDateFormat.format(requestedInterval.end)
    
    Map<String, List<TimeInterval>> bugToIntervals = new TreeMap<String, List<TimeInterval>>()
    
    String requestURLString = "$baseURL/api.asp?cmd=listIntervals&dtStart=$searchStartString"+
        "&dtEnd=$searchEndString&token=$logonToken"
    def intervalElems = new XmlSlurper().parse(requestURLString).intervals.children()
    for (intervalElem in intervalElems) {
      String bugID = intervalElem.ixBug.text()
      if (!matchingBugIDs.containsKey(bugID)) {
        continue
      }
      
      String startString = intervalElem.dtStart.text()
      Date start = fogBugzDateFormat.parse(startString)
      String endString = intervalElem.dtEnd.text()
      Date end = null
      try {
        end = fogBugzDateFormat.parse(endString)
      } catch (ParseException pe) {
        end = new Date()
      }
      
      List<TimeInterval> tempIntervalList = bugToIntervals.get(bugID)
      if (tempIntervalList == null) {
        tempIntervalList = new ArrayList<TimeInterval>()
        bugToIntervals.put(bugID, tempIntervalList)
      }
      tempIntervalList.add(new TimeInterval(start:start, end:end))
    }
    
    if (format.equals('Readable')) {
      NumberFormat numFormat = NumberFormat.getInstance()
      numFormat.setMaximumFractionDigits(2)
      writer << 'Intervals:\n\n'
      double totalDuration = 0.0
      bugToIntervals.each { caseNum, intervals ->
        String title = matchingBugIDs[caseNum]
        writer << "Case#$caseNum: $title\n"
        double totalCaseDuration = 0.0
        intervals.each {
          double duration = it.durationInHours
          totalCaseDuration += duration
          writer << "$it.start to $it.end, Duration (hrs): ${numFormat.format(duration)}\n"
        }
        writer << "Total Case Duration (hrs): ${numFormat.format(totalCaseDuration)}\n\n"
        totalDuration += totalCaseDuration
      }
      
      writer << "Overall Total Duration (hrs): ${numFormat.format(totalDuration)}"
    } else if (format.equals('CSV')) {
      def dateFormat = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss')
      writer << "Case No.,Start Time,End Time,Duration (hrs)\n"
      bugToIntervals.each { caseNum, intervals ->
        intervals.each {
          writer << "$caseNum,${dateFormat.format(it.start)},${dateFormat.format(it.end)},"+
          "$it.durationInHours\n"
        }
      }
    }
    
    writer.close()
    
    if (initialFilterID != null) {
      setCurrentFilterID(initialFilterID) //restore filter to original
    }
  }
  
  private String getCurrentFilterID() {
    String requestURLString = "$baseURL/api.asp?token=$logonToken&cmd=search&max=1"
    return new XmlSlurper().parse(requestURLString).sFilter.text()
  }
  
  private void setCurrentFilterID(String filterID) {
    String requestURLString = "$baseURL/api.asp?token=$logonToken&cmd=saveFilter&sFilter=$filterID"
    new URL(requestURLString).openStream()
  }
  
  private Map<String, String> setFilterAndFindMatches(String filterName) {
    Map<String, String> matchingBugIDs = new HashMap<String, String>()
    
    if (filterName != null) {
      String filterID = filterNameToIDMap.get(filterName)
      if (filterID != null) {
        setCurrentFilterID(filterID)
      }
    }
    
    String requestURLString = "$baseURL/api.asp?token=$logonToken&cmd=search&cols=sTitle"
    def cases = new XmlSlurper().parse(requestURLString).cases.'case'
    cases.each { matchingBugIDs[it.@ixBug.text()] = it.sTitle.text() }
    
    return matchingBugIDs
  }
  
  private Map<String, String> buildFilterNameToIDMap() {
    Map<String, String> filterNameToIDMap = new HashMap<String, String>()
    String requestURLString = "$baseURL/api.asp?token=$logonToken&cmd=listFilters"
    def filterElems = new XmlSlurper().parse(requestURLString).filters.filter
    filterElems.each{ filterNameToIDMap[it.text()] = it.@sFilter.text() }
    
    return filterNameToIDMap
  }
  
  private TimeInterval roundToMidnightAndConvertTimeZone(Date startDate, Date endDate,
                                                         TimeZone otherTimeZone) {
    TimeInterval returnVal = new TimeInterval()
    
    Calendar localCalendar = Calendar.getInstance()
    Calendar gmtCalendar = Calendar.getInstance(otherTimeZone)
    
    localCalendar.with {
      setTime(startDate)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
    }
    gmtCalendar.setTimeInMillis(localCalendar.getTimeInMillis())
    returnVal.start = gmtCalendar.time
    
    localCalendar.with {
      setTime(endDate)
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      add(Calendar.DATE, 1) //move to next day
    }
    gmtCalendar.timeInMillis = localCalendar.timeInMillis
    returnVal.end = gmtCalendar.time
    
    return returnVal
  }
  
  void logoff() {
    if (logonToken != null) {
      URL logoffURL = new URL("$baseURL/api.asp?cmd=logoff&token=$logonToken")
      try {
        logoffURL.getContent()
      } catch (IOException e) {
        e.printStackTrace()
      }
      
      logonToken = null
      filterNameToIDMap = null
    }
  }
}

class TimeInterval {
  Date start
  Date end
  
  double getDurationInHours() {
    return (end.time - start.time) / 3600000.0 // milliseconds per hour
  }
}
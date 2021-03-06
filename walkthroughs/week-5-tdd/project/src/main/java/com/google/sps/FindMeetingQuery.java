// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;

import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;

public final class FindMeetingQuery {
    public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {

        Collection<TimeRange> validTimes = new ArrayList<>();

        // If request covers more than a day, return no valid time.
        if (request.getDuration() > TimeRange.WHOLE_DAY.duration()) {
            return validTimes;
        }

        long requestDuration = request.getDuration();

        Collection<TimeRange> invalidTimes = checkConflicts(events, request, true);
        Collections.sort((ArrayList) invalidTimes, TimeRange.ORDER_BY_START);

        validTimes = getValidTimes(invalidTimes, requestDuration);

        if (request.getOptionalAttendees().isEmpty()) {
            return validTimes;
        } else {
            if (validTimes.contains(TimeRange.WHOLE_DAY)) {
                validTimes = getValidTimes(checkConflicts(events, request, false), requestDuration);
                return validTimes;
            }
            Collection<TimeRange> invalidOptionalTimes = checkConflicts(events, request, false);           
            Collection<TimeRange> validOptionalTimes = new ArrayList<>();

            for (TimeRange vt : validTimes) {
                boolean isValid = true;
                for (TimeRange ivt : invalidOptionalTimes) {
                    if (vt.overlaps(ivt) || vt.contains(ivt) || ivt.contains(vt)) {
                        isValid = false;
                        break;
                    }
                }
                if (isValid) {
                    validOptionalTimes.add(vt);
                }
            }

            if (validOptionalTimes.isEmpty()) {
                return validTimes;
            }
    
            return validOptionalTimes;
        }
        
    }

    // Loop through each event and check where overlaps are between
    // mandatory attendees's prior commitments.
    private Collection<TimeRange> checkConflicts(Collection<Event> events, MeetingRequest request, boolean isMandatory) {
        Collection<TimeRange> invalidTimes = new ArrayList<>();
        Collection<String> requestAttendees;
        if (isMandatory){
            requestAttendees = new ArrayList<String>(request.getAttendees());
        } else {
            requestAttendees = new ArrayList<String>(request.getOptionalAttendees());
        }

        for (Event event : events) {
            TimeRange eventTime = event.getWhen();
            Set<String> eventAttendees = new HashSet<String>(event.getAttendees());
          
            for (String attendee : requestAttendees) {
                if (eventAttendees.contains(attendee)) {
                    invalidTimes.add(eventTime);
                    break;
                }
            }
        }

        return invalidTimes;
    }

    private Collection<TimeRange> getValidTimes(Collection<TimeRange> invalidTimes, long requestDuration) {
        // Loop through each of the time conflicts
        // to determine which time ranges are available.
        // Only add time ranges that have a valid
        // duration length. 
        Collection<TimeRange> validTimes = new ArrayList<>();

        int startTime = TimeRange.START_OF_DAY;
        int endTime = TimeRange.START_OF_DAY;
        TimeRange prevTimeRange = TimeRange.fromStartEnd(startTime, endTime, false);
        for (TimeRange timeRange : invalidTimes) {
            if (prevTimeRange.contains(timeRange)) {
                prevTimeRange = timeRange;
                continue;
            }
            if (prevTimeRange.overlaps(timeRange)) {
                startTime = timeRange.end();
                endTime = timeRange.end();
                prevTimeRange = timeRange;
                continue;
            }
            endTime = timeRange.start();
            TimeRange potentialTimeRange = TimeRange.fromStartEnd(startTime, endTime, false);
            if (potentialTimeRange.duration() >= requestDuration) {
                validTimes.add(potentialTimeRange);
            }
            startTime = timeRange.end();
            prevTimeRange = timeRange;
        }

        // Gets last time space (if applicable)
        TimeRange lastTime = TimeRange.fromStartEnd(startTime, TimeRange.END_OF_DAY, true);
        if (lastTime.duration() >= requestDuration) {
            validTimes.add(lastTime);
        }

        return validTimes;
    }
}
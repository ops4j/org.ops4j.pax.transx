/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ops4j.pax.transx.jdbc;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class KnownSQLStateExceptionSorter extends ConfigurableSQLStateExceptionSorter {

    private static final Collection<String> ALLOWED = new HashSet<>(Arrays.asList(
            //These values are from http://publib.boulder.ibm.com/infocenter/idshelp/v10/index.jsp?topic=/com.ibm.esqlc.doc/esqlc211.htm
            //allegedly they are all defined by ansi and x/open standards.
            "00000",  //success... should not be in an exception
            /* 01 codes are all OK
            "01000",  //success with warning
            "01003",  //null value eliminated in set function
            "01004",  //string data, right truncation
            "01005",  //insuffient item descriptor areas
            "01006",  //privilege not revoked
            "01007",  //privilege not granted
             */
            "02000",  //do data found or end of data reached
            "07000",  //dynamic sql error
            "07001",  //using clause does not match dynamic parameters
            "07002",  //using clause does not match target specifications
            "07003",  //cursor specification cannot be executed
            "07004",  //using cluase required for dynamic parameters
            "07005",  //prepared statement is not a cursor specification
            "07006",  //restricted data type attribute fiolation
            "07008",  //invalid descriptor count
            "07009",  //invalid descriptor index
            "08007",  //transaction state unknown
            "22000",  //cardinality violation
            "22001",  //string data, right truncation
            "22002",  //null value, no indicator parameter
            "22003",  //numeric value out of range
            "22005",  //error in assignment
            "22012",  //division by zero
            "22019",  //invalid escape character
            "22024",  //unterminated string
            "22025",  //invalid escape sequence
            "22027",  //data exception trim error
            "23000",  //integrity constraint violation
            "24000",  //invalid cursor state
            "25000",  //invalid transaction state
            "2B000",  //dependent privilege descriptors still exist
            "2D000",  //invalid transaction termination
            "26000",  //invalid sql statement identifier
//            "2E000",  //invalid connection name
            "28000",  //invalid user-authorization specification
            "33000",  //invalid sql descriptor name
            "34000",  //invalid cursor name
            "35000",  //invalid exception number
            "37000",  //syntax error or access violation in prepare or execute immediate
            "3C000",  //duplicate cursor name
            "40000",  //transaction rollback
            "40003",  //statement completion unknown
            "42000"  //syntax error or access violation
            /* any codes starting with "S" are db specific
            "S0000",  //invalid name
            "S0001",  //base table or view already exists
            "S0002",  //base table not found
            "S0011",  //index already exists
            "S0021",  //column already exists
            "S1001",  //memory allocation error message
             */

            /* definitely fatal codes
            "01002",  //disconnect error.  This will apparently only be set when you try to disconnect, so the connection would not be in the pool anyway.
            "08000",  //connection error
            "08001",  //server rejected connection
            "08002",  //connection name in use
            "08003",  //connection does not exist
            "08004",  //client unable to establish connection
            "08006",  //connection error
            "08S01",  //communication failure
             */

    ));

    public KnownSQLStateExceptionSorter() {
        super(ALLOWED);
    }

    @Override
    protected boolean checkSQLState(String sqlState) {
        //all "01" states are non-fatal.  See note on 01002 above
        if (sqlState.startsWith("01")) {
            return false;
        }
        return super.checkSQLState(sqlState);
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.tusk.hadoop;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.util.Bytes;
import org.jboss.tusk.hadoop.EmbeddedServerHelper;
import org.jboss.tusk.hadoop.HBaseException;
import org.jboss.tusk.hadoop.HBaseFacade;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HBaseFacadeTest {

   //private static final boolean USE_EMBEDDED = true;
   private static final boolean USE_EMBEDDED = false;

   private static HBaseFacade HBF;

   private static final String TABLE_MESSAGE = "messages_" + System.currentTimeMillis();
   private static final String COL_FAMILY_DATA = "data";
   private static final String COL_FAMILY_METADATA = "metadata";
   private static final String QUANTIFIER_VALUE = "value";
   private static List<String> COL_FAMILIES = new ArrayList<String>();

   private List<String> COL_FAMILIES_WITH_DATA = new ArrayList<String>();

   private Map<String, Map<String, byte[]>> DATA_MAP_1 = new HashMap<String, Map<String, byte[]>>(1);
   private Map<String, byte[]> DATA_CELLS_1 = new HashMap<String, byte[]>();
   private Map<String, byte[]> METADATA_CELLS_1 = new HashMap<String, byte[]>();

   private Map<String, Map<String, byte[]>> DATA_MAP_2 = new HashMap<String, Map<String, byte[]>>(1);
   private Map<String, byte[]> DATA_CELLS_2 = new HashMap<String, byte[]>();

   private Map<String, Map<String, byte[]>> DATA_MAP_3 = new HashMap<String, Map<String, byte[]>>(1);
   private Map<String, byte[]> DATA_CELLS_3 = new HashMap<String, byte[]>();

   private Map<String, Map<String, byte[]>> DATA_MAP_4 = new HashMap<String, Map<String, byte[]>>(1);
   private Map<String, byte[]> DATA_CELLS_4 = new HashMap<String, byte[]>();

   // data 1 - this one has metadata to go along with it
   private byte[] data1 = "<message><name>data1</name><value>something1</value></message>"
            .getBytes();
   private String metadataStr1 = "<metadata>"
            + "<field><name>field1.1</name><value>blue1</value></field>"
            + "<field><name>field1.2</name><value>orange1</value></field>"
            + "<field><name>field1.3</name><value>purple1</value></field>" + "</metadata>";
   private byte[] metadata1 = metadataStr1.getBytes();

   // data 2
   private byte[] data2 = "<message><name>data2</name><value>something2</value></message>"
            .getBytes();

   // data 3
   private byte[] data3 = "<message><name>data3</name><value>something3</value></message>"
            .getBytes();

   // data 4
   private byte[] data4 = "<message><name>data4</name><value>something4</value></message>"
            .getBytes();

   // message keys
   private String messageKey1 = "message1";
   private String messageKey2 = "message2";
   private String messageKey3 = "message3";
   private String messageKey4 = "message4";

   // to simulate maintaining a separate expiration metadata table
   private static final String EXP_KEY_PREFIX = "exp_";
   private static final String COL_FAMILY_EXP = "expiration";
   private static final String EXP_VALUE_FIELD = "v";

   private static EmbeddedServerHelper embedded = null;
   
   static {
      if (USE_EMBEDDED) {
         embedded = new EmbeddedServerHelper();
         
         try {
            embedded.setup();
         } catch (Exception ex) {
            System.err.println(ex.getClass().getName() + " occured starting " +
                     "up embedded HBase server: " + ex.getMessage());
         }
      }
   }

   @Before
   public void setup() throws Exception {
      Map<String, String> props = new HashMap<String, String>();
      if (USE_EMBEDDED) {
         props.put("hbase.zookeeper.property.clientPort", Integer.toString(embedded.zooKeeperPort));
      }
      HBF = new HBaseFacade(props);

      // prepare data for tests
      COL_FAMILIES_WITH_DATA.add(COL_FAMILY_DATA);
      COL_FAMILIES_WITH_DATA.add(COL_FAMILY_METADATA);

      DATA_CELLS_1.put(QUANTIFIER_VALUE, data1);
      DATA_MAP_1.put(COL_FAMILY_DATA, DATA_CELLS_1);
      METADATA_CELLS_1.put(QUANTIFIER_VALUE, metadata1);
      DATA_MAP_1.put(COL_FAMILY_METADATA, METADATA_CELLS_1);

      DATA_CELLS_2.put(QUANTIFIER_VALUE, data2);
      DATA_MAP_2.put(COL_FAMILY_DATA, DATA_CELLS_2);

      DATA_CELLS_3.put(QUANTIFIER_VALUE, data3);
      DATA_MAP_3.put(COL_FAMILY_DATA, DATA_CELLS_3);

      DATA_CELLS_4.put(QUANTIFIER_VALUE, data4);
      DATA_MAP_4.put(COL_FAMILY_DATA, DATA_CELLS_4);

      COL_FAMILIES.add(COL_FAMILY_DATA);
      COL_FAMILIES.add(COL_FAMILY_METADATA);
      COL_FAMILIES.add(COL_FAMILY_EXP);

      // prepare table for tests
      HBF.createTable(TABLE_MESSAGE, COL_FAMILIES);

   }

   @After
   public void tearDown() throws Exception {
      HBF.deleteTable(TABLE_MESSAGE);
   }

   /**
    * Tests creation and deletion of tables, as well as the method to check whether a table exists.
    * 
    * @throws HBaseException
    */
   @Test
   public void tableCreateAndDelete() throws HBaseException {
      String testCF = "testCF";
      String testDataVal = "This is some data.";
      String testTable = TABLE_MESSAGE + "_test";
      String testField = "testField";
      String testKey = "testKey";

      List<String> colFamilies = new ArrayList<String>();
      colFamilies.add(testCF);

      assertTrue("Table already exists.", !HBF.tableExists(testTable));

      try {
         HBF.createTable(testTable, colFamilies);
      } catch (HBaseException ex) {
         if (ex.getCause() instanceof TableExistsException) {
            System.err.println("Cannot test createTable because the " + testTable
                     + " table already exists.");
            return;
         } else {
            throw ex;
         }
      }

      assertTrue("Table not created properly.", HBF.tableExists(testTable));

      Map<String, Map<String, byte[]>> testData = new HashMap<String, Map<String, byte[]>>();

      Map<String, byte[]> dataCells1 = new HashMap<String, byte[]>();
      dataCells1.put(testField, testDataVal.getBytes());
      testData.put(colFamilies.get(0), dataCells1);
      HBF.addRow(testTable, testKey, testData);

      testData = HBF.readRow(testTable, testKey, colFamilies);
      assertTrue(Arrays.equals(testData.get(colFamilies.get(0)).get(testField),
               testDataVal.getBytes()));

      HBF.removeRow(testTable, testKey);

      HBF.deleteTable(testTable);

      assertTrue("Table not deleted properly.", !HBF.tableExists(testTable));
   }

   /**
    * Tests writing a row to the table, reading its data, and removing it. The row that is written
    * has two column families - one for data and one for metadata.
    * 
    * @throws HBaseException
    */
   @Test
   public void writeAddRow() throws HBaseException {
      // write the row
      HBF.addRow(TABLE_MESSAGE, messageKey1, DATA_MAP_1);

      // query the row
      Map<String, Map<String, byte[]>> resultMap = HBF.readRow(TABLE_MESSAGE, messageKey1,
               COL_FAMILIES_WITH_DATA);

      assertTrue(resultMap.containsKey(COL_FAMILY_DATA));
      Map<String, byte[]> columnFamilyData = resultMap.get(COL_FAMILY_DATA);
      byte[] resultData = columnFamilyData.get(QUANTIFIER_VALUE);
      assertTrue(Arrays.equals(resultData, data1));

      assertTrue(resultMap.containsKey(COL_FAMILY_METADATA));
      Map<String, byte[]> columnFamilyMetadata = resultMap.get(COL_FAMILY_METADATA);
      byte[] resultMetadata = columnFamilyMetadata.get(QUANTIFIER_VALUE);
      assertTrue(Arrays.equals(resultMetadata, metadata1));

      // remove the row and verify that it's gone
      HBF.removeRow(TABLE_MESSAGE, messageKey1);
      resultMap = HBF.readRow(TABLE_MESSAGE, messageKey1, COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());
   }

   /**
    * Tests writing messages, scanning the table, and removing the messages.
    * 
    * @throws HBaseException
    */
   @Test
   public void writeAndScanMessageData() throws HBaseException {
      HBF.addRow(TABLE_MESSAGE, messageKey1, DATA_MAP_1);
      HBF.addRow(TABLE_MESSAGE, messageKey2, DATA_MAP_2);
      HBF.addRow(TABLE_MESSAGE, messageKey3, DATA_MAP_3);
      HBF.addRow(TABLE_MESSAGE, messageKey4, DATA_MAP_4);

      Map<String, byte[]> dataMap = HBF.scan(TABLE_MESSAGE, 4, COL_FAMILY_DATA, QUANTIFIER_VALUE);

      assertTrue(Arrays.equals(dataMap.get(messageKey1), data1));
      assertTrue(Arrays.equals(dataMap.get(messageKey2), data2));
      assertTrue(Arrays.equals(dataMap.get(messageKey3), data3));
      assertTrue(Arrays.equals(dataMap.get(messageKey4), data4));

      HBF.removeRow(TABLE_MESSAGE, messageKey1);
      HBF.removeRow(TABLE_MESSAGE, messageKey2);
      HBF.removeRow(TABLE_MESSAGE, messageKey3);
      HBF.removeRow(TABLE_MESSAGE, messageKey4);
   }

   /**
    * Tests writing messages, scanning the table for the keys, and removing the messages.
    * 
    * @throws HBaseException
    */
   @Test
   public void writeAndScanMessageKeys() throws HBaseException {
      HBF.addRow(TABLE_MESSAGE, messageKey1, DATA_MAP_1);
      HBF.addRow(TABLE_MESSAGE, messageKey2, DATA_MAP_2);
      HBF.addRow(TABLE_MESSAGE, messageKey3, DATA_MAP_3);
      HBF.addRow(TABLE_MESSAGE, messageKey4, DATA_MAP_4);

      Set<Object> keys = HBF.scanForKeys(TABLE_MESSAGE);
      assertTrue("Did not return a key", keys.contains(messageKey1));
      assertTrue("Did not return a key", keys.contains(messageKey2));
      assertTrue("Did not return a key", keys.contains(messageKey3));
      assertTrue("Did not return a key", keys.contains(messageKey4));

      HBF.removeRow(TABLE_MESSAGE, messageKey1);
      HBF.removeRow(TABLE_MESSAGE, messageKey2);
      HBF.removeRow(TABLE_MESSAGE, messageKey3);
      HBF.removeRow(TABLE_MESSAGE, messageKey4);
   }

   /**
    * Tests writing messages and removing them in a bulk delete.
    * 
    * @throws HBaseException
    */
   @Test
   public void writeAndRemoveRows() throws HBaseException {
      HBF.addRow(TABLE_MESSAGE, messageKey1, DATA_MAP_1);
      HBF.addRow(TABLE_MESSAGE, messageKey2, DATA_MAP_2);
      HBF.addRow(TABLE_MESSAGE, messageKey3, DATA_MAP_3);
      HBF.addRow(TABLE_MESSAGE, messageKey4, DATA_MAP_4);

      Set<Object> keys = new HashSet<Object>();
      keys.add(messageKey1);
      keys.add(messageKey2);
      keys.add(messageKey3);
      keys.add(messageKey4);

      HBF.removeRows(TABLE_MESSAGE, keys);

      Map<String, Map<String, byte[]>> resultMap = HBF.readRow(TABLE_MESSAGE, messageKey1,
               COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());

      resultMap = HBF.readRow(TABLE_MESSAGE, messageKey2, COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());

      resultMap = HBF.readRow(TABLE_MESSAGE, messageKey3, COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());

      resultMap = HBF.readRow(TABLE_MESSAGE, messageKey4, COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());
   }

   /**
    * Tests reading multiple rows and removing them.
    * 
    * @throws HBaseException
    */
   @Test
   public void readRowsAndDelete() throws HBaseException {
      long tsBase = System.currentTimeMillis();
      Set<Object> expKeys = new HashSet<Object>();
      List<String> expColFamilies = new ArrayList<String>();
      expColFamilies.add(COL_FAMILY_EXP);

      // add some data rows and corresponding expiration metadata rows
      HBF.addRow(TABLE_MESSAGE, messageKey1, DATA_MAP_1);
      String expKey1 = EXP_KEY_PREFIX + String.valueOf(tsBase);
      expKeys.add(expKey1);
      HBF.addRow(TABLE_MESSAGE, expKey1, makeExpirationMap(messageKey1));

      HBF.addRow(TABLE_MESSAGE, messageKey2, DATA_MAP_2);
      String expKey2 = EXP_KEY_PREFIX + String.valueOf(tsBase + 2);
      expKeys.add(expKey2);
      HBF.addRow(TABLE_MESSAGE, expKey2, makeExpirationMap(messageKey2));

      HBF.addRow(TABLE_MESSAGE, messageKey3, DATA_MAP_3);
      String expKey3 = EXP_KEY_PREFIX + String.valueOf(tsBase + 4);
      expKeys.add(expKey3);
      HBF.addRow(TABLE_MESSAGE, expKey3, makeExpirationMap(messageKey3));

      HBF.addRow(TABLE_MESSAGE, messageKey4, DATA_MAP_4);
      String expKey4 = EXP_KEY_PREFIX + String.valueOf(tsBase + 6);
      expKeys.add(expKey4);
      HBF.addRow(TABLE_MESSAGE, expKey4, makeExpirationMap(messageKey4));

      // read the rows using the key prefix and a timestamp that is halfway through the entries
      // (should return keys for just 2 previously added items)
      Map<String, Map<String, Map<String, byte[]>>> rowsToPurge = HBF.readRows(TABLE_MESSAGE,
               EXP_KEY_PREFIX, tsBase + 3, COL_FAMILY_EXP, EXP_VALUE_FIELD);

      Set<Object> keysToDelete = new HashSet<Object>();
      Set<Object> expKeysToDelete = new HashSet<Object>();
      for (Entry<String, Map<String, Map<String, byte[]>>> entry : rowsToPurge.entrySet()) {
         assertTrue(expKeys.contains(entry.getKey()));
         expKeysToDelete.add(entry.getKey());

         byte[] targetKeyBytes = entry.getValue().get(COL_FAMILY_EXP).get(EXP_VALUE_FIELD);
         String targetKey = Bytes.toString(targetKeyBytes);
         keysToDelete.add(targetKey);
      }

      assertTrue(keysToDelete.contains(messageKey1));
      assertTrue(keysToDelete.contains(messageKey2));
      assertTrue(!keysToDelete.contains(messageKey3));
      assertTrue(!keysToDelete.contains(messageKey4));

      // read the rows using the key prefix and a timestamp greater than the timestamps from the
      // entries
      // (should return keys for all previously added items)
      rowsToPurge = HBF.readRows(TABLE_MESSAGE, EXP_KEY_PREFIX, tsBase + 100, COL_FAMILY_EXP,
               EXP_VALUE_FIELD);

      keysToDelete = new HashSet<Object>();
      expKeysToDelete = new HashSet<Object>();
      for (Entry<String, Map<String, Map<String, byte[]>>> entry : rowsToPurge.entrySet()) {
         assertTrue(expKeys.contains(entry.getKey()));
         expKeysToDelete.add(entry.getKey());

         byte[] targetKeyBytes = entry.getValue().get(COL_FAMILY_EXP).get(EXP_VALUE_FIELD);
         String targetKey = Bytes.toString(targetKeyBytes);
         keysToDelete.add(targetKey);
      }

      assertTrue(keysToDelete.contains(messageKey1));
      assertTrue(keysToDelete.contains(messageKey2));
      assertTrue(keysToDelete.contains(messageKey3));
      assertTrue(keysToDelete.contains(messageKey4));

      // now delete the data and expiration metadata rows
      HBF.removeRows(TABLE_MESSAGE, keysToDelete);
      HBF.removeRows(TABLE_MESSAGE, expKeysToDelete);

      // make sure rows were deleted
      Map<String, Map<String, byte[]>> resultMap = HBF.readRow(TABLE_MESSAGE, messageKey1,
               COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());
      resultMap = HBF.readRow(TABLE_MESSAGE, expKey1, expColFamilies);
      assertTrue(resultMap.isEmpty());

      resultMap = HBF.readRow(TABLE_MESSAGE, messageKey2, COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());
      resultMap = HBF.readRow(TABLE_MESSAGE, expKey2, expColFamilies);
      assertTrue(resultMap.isEmpty());

      resultMap = HBF.readRow(TABLE_MESSAGE, messageKey3, COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());
      resultMap = HBF.readRow(TABLE_MESSAGE, expKey3, expColFamilies);
      assertTrue(resultMap.isEmpty());

      resultMap = HBF.readRow(TABLE_MESSAGE, messageKey4, COL_FAMILIES_WITH_DATA);
      assertTrue(resultMap.isEmpty());
      resultMap = HBF.readRow(TABLE_MESSAGE, expKey4, expColFamilies);
      assertTrue(resultMap.isEmpty());
   }

   private Map<String, Map<String, byte[]>> makeExpirationMap(String value) {
      Map<String, byte[]> expValMap = new HashMap<String, byte[]>();
      expValMap.put(EXP_VALUE_FIELD, Bytes.toBytes(value));
      Map<String, Map<String, byte[]>> expCfMap = new HashMap<String, Map<String, byte[]>>();
      expCfMap.put(COL_FAMILY_EXP, expValMap);

      return expCfMap;
   }

}

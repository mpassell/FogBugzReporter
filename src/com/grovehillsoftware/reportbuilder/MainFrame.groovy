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

import com.toedter.calendar.JDateChooser
import groovy.swing.SwingBuilder
import java.awt.BorderLayout as BL
import java.text.DateFormat
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.WindowConstants as WC

/**
 *
 * @author Matt
 */
class MainFrame {
  static final String URL = 'URL'
  static final String USER = 'user'
  static final String PASSWORD = 'password'
  static final String FORMAT = 'reportFormat'
  static final String START_DATE = 'startDate'
  static final String END_DATE = 'endDate'
  static final String INITIAL_DIR = 'initialDir'

  def swing
  def login
  def logout
  def saveReport
  def generateReport
  def frame

  public MainFrame() {
    swing = new SwingBuilder();
    Preferences prefs = Preferences.userRoot().node('/com/grovehillsoftware/reportbuilder')
    def generator = new TimeSheetReportGenerator()

    login = swing.action(name:'Login', closure: {
      generator.logon(swing.URL.text, swing.user.text, swing.password.text)
      new TreeSet(generator.filterNameToIDMap.keySet()).each { swing.filter.addItem(it) }
      login.enabled = false
      logout.enabled = true
      generateReport.enabled = true
      swing.startDate.enabled = true;
      swing.endDate.enabled = true;
      swing.reportFormat.enabled = true;
      swing.filter.enabled = true;
    })

    logout = swing.action(name:'Logout', enabled:false, closure: {
      generator.logoff()
      swing.filter.removeAllItems()
      login.enabled = true
      logout.enabled = false
      generateReport.enabled = false
      swing.startDate.enabled = false;
      swing.endDate.enabled = false;
      swing.reportFormat.enabled = false;
      swing.filter.enabled = false;
    })

    saveReport = swing.action(name:'Save Report', closure: {
      initialDir = prefs.get(INITIAL_DIR, null)
      outputChooser =
        swing.fileChooser(currentDirectory:initialDir != null ? new File(initialDir) : null)
      int returnVal = outputChooser.showSaveDialog(swing.mainPanel)
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        selectedFile = outputChooser.selectedFile
        prefs.put(INITIAL_DIR, selectedFile.parent)

        PrintWriter pw = new PrintWriter(new FileWriter(selectedFile))
        pw.write(swing.reportText.text)
        pw.close()
      }
    })

    generateReport = swing.action(name:'Generate Report', enabled:false, closure: {
      def sw = new StringWriter()
      generator.outputReport(swing.filter.selectedItem, swing.startDate.date, swing.endDate.date,
                             swing.reportFormat.selectedItem, new PrintWriter(sw))
      def reportDialog = swing.dialog(title:'Report', owner:frame, modal:true) {
        panel(layout:new BL()) {
          scrollPane {
            textArea(id:'reportText', constraints:BL.CENTER, sw.toString())
          }
          button(action:saveReport, constraints:BL.SOUTH)
        }
      }
      reportDialog.pack();
      reportDialog.show();
    })

    frame = swing.frame(title:'FogBugzReporter', defaultCloseOperation:WC.EXIT_ON_CLOSE) {
      panel(id:'mainPanel') {
        tableLayout(cellpadding:3) {
          tr {
            td { label('Site URL:') }
            td { textField(id:URL, columns:25, prefs.get(URL, '')) }
          }
          tr {
            td { label('Username:') }
            td { textField(id:USER, columns:25, prefs.get(USER, '')) }
          }
          tr {
            td { label('Password:') }
            td { passwordField(id:PASSWORD, columns:10, prefs.get(PASSWORD, '')) }
          }
          tr {
            td { button(action:login) }
            td { button(action:logout) }
          }
          tr {
            td { label('Start Date:') }
            td { widget(id:START_DATE, enabled:false, preferredSize:[90, 20],
                        new JDateChooser(new Date(), 'MM/dd/yyyy')) }
          }
          tr {
            td { label('End Date:') }
            td { widget(id:END_DATE, enabled:false, preferredSize:[90, 20],
                        new JDateChooser(new Date(), 'MM/dd/yyyy')) }
          }
          tr {
            td { label('Report Format:') }
            td { comboBox(id:FORMAT, enabled:false, items:['Readable', 'CSV'],
                          selectedItem:prefs.get(FORMAT, 'Readable')) }
          }
          tr {
            td { label('Filter:') }
            td { comboBox(id:'filter', enabled:false) }
          }
          tr {
            td(colspan:2) { button(action:generateReport) }
          }
        }
      }
    }

    frame.windowClosing = {
      generator.logoff()
      prefs.put(URL, swing.URL.text)
      prefs.put(USER, swing.user.text)
      prefs.put(PASSWORD, swing.password.text)
      prefs.put(FORMAT, swing.reportFormat.selectedItem)
    }
    frame.pack()
    frame.show()
  }

  public static void main(String[] args) {
    new MainFrame();
  }
}


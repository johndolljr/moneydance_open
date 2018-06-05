/*************************************************************************\
* Copyright (C) 2010 The Infinite Kind, LLC
*
* This code is released as open source under the Apache 2.0 License:<br/>
* <a href="http://www.apache.org/licenses/LICENSE-2.0">
* http://www.apache.org/licenses/LICENSE-2.0</a><br />
\*************************************************************************/

package com.moneydance.modules.features.yahooqt;

import com.infinitekind.moneydance.model.*;
import com.infinitekind.util.CustomDateFormat;

import java.text.MessageFormat;
import java.util.concurrent.Callable;

/**
 * Performs a test to see if quotes/prices can be downloaded without error.
 *
 * @author Kevin Menningen - Mennē Software Solutions, LLC
 */
public class DownloadTest implements Callable<Boolean> {
  
  static final String NAME = DownloadTask.NAME;
  private final StockQuotesModel _model;
  private final ResourceProvider _resources;
  private final CustomDateFormat _dateFormat;

  DownloadTest(final StockQuotesModel model, final ResourceProvider resources) {
    _model = model;
    _resources = resources;
    _dateFormat = _model.getPreferences().getShortDateFormatter();
  }

  @Override
  public String toString() { return NAME; }
  
  public Boolean call() throws Exception {
    final String taskDisplayName = _resources.getString(L10NStockQuotes.QUOTES);
    // this is a Moneydance string that says 'Downloading {acctname}'
    String format = _model.getGUI().getStr("downloading_acct_x");
    _model.showProgress(0.0f, SQUtil.replaceAll(format, "{acctname}", taskDisplayName));

    final SecuritySymbolTableModel tableModel = _model.getTableModel();
    final int rowCount = tableModel.getRowCount();

    // initial setup
    for (int index = 0; index < rowCount; index++) {
      final SecuritySymbolTableModel.SecurityEntry entry = tableModel.getEntry(index);
      if (entry.use) {
        entry.testResult = _resources.getString(L10NStockQuotes.TEST_NOTSTARTED);
      } else {
        entry.testResult = _resources.getString(L10NStockQuotes.TEST_EXCLUDED);
      }
    }
    tableModel.refreshRow(-1);

    // download each one
    final BaseConnection historyConnection = _model.getSelectedHistoryConnection();
    final String setupError = _resources.getString(L10NStockQuotes.TEST_ERR_SETUP);
    final String skipped = _resources.getString(L10NStockQuotes.TEST_EXCLUDED);
    for (int index = 0; index < rowCount; index++) {
      final SecuritySymbolTableModel.SecurityEntry entry = tableModel.getEntry(index);
      if (entry.use) {
        final TestResult testResult = testEntry(entry, historyConnection);
        entry.testResult = (historyConnection == null) ? setupError : testResult._testResult;
        entry.toolTip = testResult._toolTip;
      } else {
        entry.testResult = skipped;
        entry.toolTip = skipped;
      }
      tableModel.refreshRow(index);
    }

    return Boolean.TRUE;
  }

  private TestResult testEntry(SecuritySymbolTableModel.SecurityEntry entry, BaseConnection historyConnection) {
    DateRange dateRange = HistoryDateRange.getRangeForSecurity(entry.currency, _model.getHistoryDays());
    final DownloadResult historyResult = getHistoryTest(entry, historyConnection, dateRange);
    TestResult testResult = new TestResult(historyResult);
    // create a verbose tooltip
    StringBuilder sb = new StringBuilder(N12EStockQuotes.HTML_BEGIN);
    // here we add information about history download even if the user chose not to update history
    if (historyConnection.canGetHistory()) {
      sb.append(N12EStockQuotes.PARA_BEGIN);
      sb.append(SQUtil.getLabelText(_resources, L10NStockQuotes.HISTORY));
      sb.append(testResult._historyResult);
    }
    sb.append(N12EStockQuotes.HTML_END);
    testResult._toolTip = sb.toString();
    // create a succinct summary test result
    sb.setLength(0);
    sb.append(N12EStockQuotes.HTML_BEGIN);
    // we don't show any results in the succinct message if they aren't updating
    final boolean downloadedHistory = historyConnection.canGetHistory() &&
            _model.isHistoricalPriceSelected();
    if (downloadedHistory) {
      sb.append(getSuccessIcon(testResult._historySuccess));
      sb.append(N12EStockQuotes.SPACE);
      sb.append(_resources.getString(L10NStockQuotes.HISTORY));
    }
    sb.append(N12EStockQuotes.HTML_END);
    testResult._testResult = sb.toString();
    return testResult;
  }

  private String getSuccessIcon(boolean success) {
    if (success) {
      return N12EStockQuotes.GREEN_FONT_BEGIN + "&#x2714;" + N12EStockQuotes.FONT_END;
    }
    return N12EStockQuotes.RED_FONT_BEGIN + "&#x2716;" + N12EStockQuotes.FONT_END;
  }

  private DownloadResult getHistoryTest(SecuritySymbolTableModel.SecurityEntry entry,
                                        BaseConnection connection, DateRange dateRange) {
    connection.setDefaultCurrency();
    try {
      if (!_model.isHistoricalPriceSelected()) {
        return new DownloadResult(_resources.getString(L10NStockQuotes.NO_UPDATE), 1);
      }
      // we do not store these results, we just download them
      final StockHistory history = connection.getHistory(entry.currency, dateRange, false);
      if (history == null) {
        return new DownloadResult(_resources.getString(L10NStockQuotes.ERROR_NO_SYMBOL), 1);
      }
      int errors = history.getErrorCount();
      int records = history.getRecordCount();
      if (errors > 0) {
        if (records > 0) {
          final String message = MessageFormat.format(
                  _resources.getString(L10NStockQuotes.TEST_SOME_SUCCESS_FMT),
                  Integer.toString(records), Integer.toString(errors));
          final DownloadResult result = new DownloadResult(message, errors);
          result.historyRecordCount = records;
        }
        final String message = MessageFormat.format(
                _resources.getString(L10NStockQuotes.TEST_ERROR_FMT),
                Integer.toString(errors));
        final DownloadResult result = new DownloadResult(message, errors);
        result.historyRecordCount = 0;
        return result;
      }
      if (records == 0) {
        return new DownloadResult(_resources.getString(L10NStockQuotes.TEST_NO_DATA), errors);
      }
      final String message = MessageFormat.format(
              _resources.getString(L10NStockQuotes.TEST_SUCCESS_FMT),
              Integer.toString(records));
      final DownloadResult result = new DownloadResult(message, 0);
      result.historyRecordCount = records;
      return result;
    } catch (DownloadException downEx) {
      return new DownloadResult(downEx.getMessage(), 1);
    }
  }
  
  private class TestResult {
    boolean _historySuccess = false;
    String _historyResult;
    String _testResult;
    String _toolTip;
    String _price;

    public TestResult(DownloadResult downloadResult) {
      _historySuccess = (downloadResult.historyErrorCount == 0 && !downloadResult.currentError);
      _historyResult = downloadResult.displayMessage;
      _price = downloadResult.currentResult;

      StringBuilder sb = new StringBuilder(N12EStockQuotes.HTML_BEGIN);
      // here we add information about history download even if the user chose not to update history
      sb.append(N12EStockQuotes.PARA_BEGIN);
      sb.append(SQUtil.getLabelText(_resources, L10NStockQuotes.HISTORY));
      sb.append(downloadResult.historyResult);
      
      sb.append(N12EStockQuotes.HTML_END);
      _toolTip = sb.toString();
    }
  }

}
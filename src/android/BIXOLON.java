package kr.co.itsm.plugin;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.bixolon.printer.BixolonPrinter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

public class BIXOLON extends CordovaPlugin {
  private static final String TAG = "BIXOLON";

  private CordovaInterface cordova;
  CallbackContext connectCallback = null;
  CallbackContext statusCallback = null;

  private static UsbManager mManager = null;
  private static PendingIntent mPermissionIntent;
  private static final String ACTION_USB_PERMISSION =
    "com.android.example.USB_PERMISSION";

  private BixolonPrinter mBixolonPrinter = null;
  private UsbDevice usbDevice = null;
  private boolean mIsConnect = true;
  private final Handler mHandler = new Handler(new Handler.Callback() {

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case BixolonPrinter.MESSAGE_USB_DEVICE_SET:
          Set<UsbDevice> usbDeviceSet = (Set<UsbDevice>) msg.obj;
          if (usbDeviceSet == null)
            return true;

          for (UsbDevice device : usbDeviceSet) {
            usbDevice = device;
            mManager.requestPermission(device, mPermissionIntent);

            return true;
          }

          if (connectCallback != null)
            connectCallback.error("Cannot found USB device");

          break;
        case BixolonPrinter.MESSAGE_STATE_CHANGE:
          switch (msg.arg1) {
            case BixolonPrinter.STATE_CONNECTED:
              if (connectCallback != null)
                connectCallback.success("BIXOLON: connected");

              mIsConnect = true;
              break;
            case BixolonPrinter.STATE_NONE:
              if (connectCallback != null)
                connectCallback.error("BIXOLON: failed to connect");

              mIsConnect = false;
              break;
          }
          break;
        case BixolonPrinter.MESSAGE_DEVICE_NAME:
          String connectedDeviceName = msg.getData().getString(BixolonPrinter.KEY_STRING_DEVICE_NAME);
          break;

        case BixolonPrinter.MESSAGE_TOAST:
          Log.i(TAG, msg.getData().getString(BixolonPrinter.KEY_STRING_TOAST));
          break;

        case BixolonPrinter.MESSAGE_READ:
          if (msg.arg1 == BixolonPrinter.PROCESS_GET_STATUS) {
            if (msg.arg2 == BixolonPrinter.STATUS_NORMAL) {
              statusCallback.success();
            } else {
              StringBuffer buffer = new StringBuffer();
              if ((msg.arg2 & BixolonPrinter.STATUS_COVER_OPEN) == BixolonPrinter.STATUS_COVER_OPEN) {
                buffer.append("주방프린터 커버가 열림\n");
              }
              if ((msg.arg2 & BixolonPrinter.STATUS_PAPER_NOT_PRESENT) == BixolonPrinter.STATUS_PAPER_NOT_PRESENT) {
                buffer.append("주방프린터 용지 없음\n");
              }

              statusCallback.error(buffer.toString());
            }

          }
          break;


      }

      return true;
    }
  });

  private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            if(device != null){
              //call method to set up device communication
              Log.i(TAG, "permission granted for device " + device);
            }
          }
          else {
            Log.i(TAG, "permission denied for device " + device);
          }
        }
      }
    }
  };

  public static CordovaWebView gWebView;

  public BIXOLON() {}

  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    this.cordova = cordova;
    gWebView = webView;

    mManager = (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);
    mPermissionIntent = PendingIntent.getBroadcast(cordova.getActivity(), 0, new Intent("com.android.example.USB_PERMISSION"), 0);
    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    cordova.getActivity().registerReceiver(mUsbReceiver, filter);

    mBixolonPrinter = new BixolonPrinter(cordova.getActivity(), mHandler, null);
    mBixolonPrinter.findUsbPrinters();
    Log.d(TAG, "==> BIXOLON initialize");
  }

  @Override
  public void onDestroy() {
    try {
      cordova.getActivity().unregisterReceiver(mUsbReceiver);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    }

    if (mBixolonPrinter != null)
      mBixolonPrinter.disconnect();
  }

  public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
    Log.d(TAG,"==> SMTCAT execute: "+ action);

    try{
      // READY //
      if (action.equals("ready")) {
        //
        callbackContext.success();
      }
      else if (action.equals("connect")) {
        connectCallback = callbackContext;
        cordova.getThreadPool().execute(new Runnable() {
          public void run() {
            try {
              connectCallback = callbackContext;
              String host = args.getString(0);
              if (host != null && !host.equals("") && !host.equals("null") && !host.equals("undefined")) {
                mBixolonPrinter.connect(host, 9100, 5000);
              } else {
                if (usbDevice != null) {
                  mBixolonPrinter.connect(usbDevice);
                } else {
                  callbackContext.error(0);
                }
              }
            } catch (Exception e) {
              callbackContext.error(0);
            }
          }
        });
      }
      else if (action.equals("disconnect")) {
        cordova.getThreadPool().execute(new Runnable() {
          public void run() {
            mBixolonPrinter.disconnect();
            mIsConnect = false;
          }
        });
      }
      else if (action.equals("getStatus")) {
        cordova.getThreadPool().execute(new Runnable() {
          public void run() {
            if (!mIsConnect) {
              callbackContext.error("주방프린터 연결안됨");
            } else {
              statusCallback = callbackContext;
              mBixolonPrinter.getStatus();
            }
          }
        });
      }
      else if (action.equals("print")) {
        try {
          String title = args.getString(0);
          JSONArray arr = args.getJSONArray(1);

          mBixolonPrinter.lineFeed(1, false);
          mBixolonPrinter.printText(title,
            BixolonPrinter.ALIGNMENT_LEFT,
            BixolonPrinter.TEXT_ATTRIBUTE_EMPHASIZED | BixolonPrinter.TEXT_ATTRIBUTE_FONT_C,
            BixolonPrinter.TEXT_SIZE_HORIZONTAL2 | BixolonPrinter.TEXT_SIZE_VERTICAL2,
            false);
          mBixolonPrinter.printText("------------------------------------------\n" +
              "NO 메뉴명                             수량\n" +
              "------------------------------------------",
            BixolonPrinter.ALIGNMENT_LEFT,
            BixolonPrinter.TEXT_ATTRIBUTE_EMPHASIZED | BixolonPrinter.TEXT_ATTRIBUTE_FONT_A,
            BixolonPrinter.TEXT_SIZE_HORIZONTAL1 | BixolonPrinter.TEXT_SIZE_VERTICAL1,
            false);

          String strPrintData = "";
          try {
            for (int i = 0; i < arr.length(); i++) {
              JSONObject rec = arr.getJSONObject(i);
              int cnt = rec.getInt("cnt");
              JSONObject menu = rec.getJSONObject("menu");
              String name = menu.getJSONObject("name").getString("ko");
              String strOpt = "옵션: ";

              JSONArray opts = null;
              try {
                opts = rec.getJSONArray("opts");
                for (int j = 0; j < opts.length(); j++) {
                  JSONObject opt = opts.getJSONObject(j);
                  if (j != 0)
                    strOpt += ", ";
                  strOpt += opt.getString("name");
                }
              } catch (Exception e) {

              }

              strPrintData += format(i+1, name, cnt) + "\n";
              if (opts != null && opts.length() > 0)
                strPrintData += strOpt + "\n";
              strPrintData += "------------------------------------------\n";
            }
          } catch (Exception e) {

          }
          mBixolonPrinter.printText(strPrintData,
            BixolonPrinter.ALIGNMENT_LEFT,
            BixolonPrinter.TEXT_ATTRIBUTE_EMPHASIZED | BixolonPrinter.TEXT_ATTRIBUTE_FONT_A,
            BixolonPrinter.TEXT_SIZE_HORIZONTAL1 | BixolonPrinter.TEXT_SIZE_VERTICAL1,
            false);

          SimpleDateFormat df = new SimpleDateFormat("yyyy년 MM월 dd일 hh시 mm분 ss초");
          Date date = new Date();
          mBixolonPrinter.printText("주문시간: " + df.format(date),
            BixolonPrinter.ALIGNMENT_LEFT,
            BixolonPrinter.TEXT_ATTRIBUTE_EMPHASIZED | BixolonPrinter.TEXT_ATTRIBUTE_FONT_A,
            BixolonPrinter.TEXT_SIZE_HORIZONTAL1 | BixolonPrinter.TEXT_SIZE_VERTICAL1,
            false);
          mBixolonPrinter.cutPaper(5, false);

          callbackContext.success();
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
      }
      else{
        callbackContext.error("Method not found");
        return false;
      }
    }catch(Exception e){
      Log.d(TAG, "ERROR: onPluginAction: " + e.getMessage());
      callbackContext.error(e.getMessage());
      return false;
    }

    return true;
  }

  private String format(int no, String name, int cnt) {
    String str = String.format("%02d ", no);
    int n = 0;
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < 35; i++) {
      String ch = " ";
      if (name.length() > i && n < 34) {
        ch = String.valueOf(name.charAt(i));
        n += ch.matches("[a-zA-Z0-9]|\\s|\\W") ? 1 : 2;
      } else {
        n++;
      }

      if (n > 35)
        break;

      sb.append(ch);
    }

    str += sb.toString();
    str += String.format("%s개", (cnt < 10 ? " " : "") + String.valueOf(cnt));
    return str;
  }
}

# Payment Checkout Security Boundary

## Checkout modes

The activation server returns an explicit `checkoutMode` for every order:

- `hosted`: an HTTPS provider page rendered in the payment-only WebView.
- `qrcode`: a payment payload rendered locally with ZXing and never passed to WebView.

The server and Android client both reject HTTP URLs, credential-bearing HTTPS URLs, active-content
schemes, oversized/control-character payloads, and payment schemes that do not match the selected
provider. WeChat accepts `weixin:` QR payloads; Alipay accepts `alipay:` and `alipays:` payloads.

## Required WebView capabilities

JavaScript is enabled only in `configurePaymentWebView`. The hosted provider page requires it to
construct and refresh the provider-owned QR presentation. DOM storage is enabled for the same
page's first-party checkout session. No application data or native capability is exposed to page
JavaScript, and the app never installs a JavaScript interface.

The WebView permits only top-level HTTPS navigation on the exact host and port of the original
checkout URL. Payment application schemes, HTTP downgrade, cross-origin navigation, downloads,
pop-up windows, mixed content, file access, content-provider access, and third-party cookies are
blocked. SSL errors are cancelled and Safe Browsing hits return to safety.

Payment completion does not trust WebView navigation or page content. The app confirms payment by
polling the authenticated native payment-status API with the order number and poll token.

## Change requirements

Any future request for another WebView capability must document the provider behavior that needs
it, add a focused test or source-health assertion, and confirm that payment success still comes only
from the native authenticated status API.

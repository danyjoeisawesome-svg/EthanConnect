# Data safety form answers (Play Console > App content > Data safety)

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (nothing collected). Local traffic is TLS-encrypted. |
| Do you provide a way for users to request that their data is deleted? | Not applicable (no data leaves the device) |

Why "No": the app has no backend and no third-party SDKs. The screen picture and remote input go directly between the user's own TV and PC on their LAN; they are not sent to the developer or any third party, so under Google's definition they are not "collected". Pairing data is stored only on the user's own devices.

Re-check this form if you ever add analytics, crash reporting, ads or a cloud service.

# Declarations to review
* **Ads:** No ads.
* **Target audience:** 13+ / general (not designed for children).
* **Government / financial / health apps:** No.
* **Permissions declared:** INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE. No sensitive/restricted permissions.
* **Home screen app:** the TV app registers as an optional HOME launcher. There is no Play restriction on this, but describe it in the listing.

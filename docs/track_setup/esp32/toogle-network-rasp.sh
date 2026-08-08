#!/bin/bash

# Configuration Variables
IFACE="wlan0"                  # Built-in Raspberry Pi Wi-Fi interface
HOTSPOT_NAME="RacingManager"
HOTSPOT_PASS="race-4-life"
HOTSPOT_IP="192.168.10.1"

# Derived Configs
CIDR="${HOTSPOT_IP}/24"

if [ "$EUID" -ne 0 ]; then
  echo "[ERROR] Please run this script with sudo."
  exit 1
fi

show_help() {
    echo "Usage: sudo $0 [--hotspot | --wifi]"
    echo "  --hotspot  : Spin up the static 'RacingManager' AP with Local DNS & DHCP"
    echo "  --wifi     : Re-enable default client mode (DHCP / DNS)"
    exit 1
}

if [ -z "$1" ]; then
    show_help
fi

case "$1" in
    --hotspot)
        echo "[+] Stopping AdGuardHome (it binds port 53 on *all* interfaces and blocks the hotspot's DHCP/DNS)..."
        # NetworkManager runs its own internal dnsmasq for any AP-mode Wi-Fi
        # connection to serve DHCP/DNS to clients. AdGuardHome's wildcard bind
        # on :53 gets there first, so that dnsmasq always failed with "Address
        # already in use", the AP's DHCP never came up, and NetworkManager fell
        # back to the last autoconnect-enabled Wi-Fi network. Stopping it here
        # is what actually frees the port; --wifi below starts it back up.
        systemctl stop AdGuardHome >/dev/null 2>&1
        pkill -f "dnsmasq.*$HOTSPOT_IP" >/dev/null 2>&1

        echo "[+] Disabling GNOME/Ubuntu background connection drops..."
        busctl set-property org.freedesktop.NetworkManager /org/freedesktop/NetworkManager org.freedesktop.NetworkManager ConnectivityCheckEnabled b false

        echo "[+] Locking Wi-Fi chip power settings (keeps connection awake)..."
        iwconfig $IFACE power off >/dev/null 2>&1

        echo "[+] Terminating existing network alignments..."
        nmcli device set $IFACE autoconnect no
        nmcli device disconnect $IFACE >/dev/null 2>&1
        nmcli connection delete "$HOTSPOT_NAME" >/dev/null 2>&1

        echo "[+] Deploying Access Point layout..."
        nmcli connection add type wifi ifname $IFACE con-name "$HOTSPOT_NAME" ssid "$HOTSPOT_NAME" mode ap
        nmcli connection modify "$HOTSPOT_NAME" wifi-sec.key-mgmt wpa-psk wifi-sec.psk "$HOTSPOT_PASS"
        # Without an explicit proto, wpa_supplicant's AP mode infers WPA vs
        # WPA2 from the AKM list alone. Leaving PMF at its "optional" default
        # makes NetworkManager add the WPA-PSK-SHA256 AKM, which the ESP32
        # Arduino WiFi stack can't associate to at all; disabling PMF removes
        # that AKM but then leaves a bare 'WPA-PSK' that infers legacy
        # WPA1/TKIP instead of WPA2 — still not something the ESP32 joins.
        # Pin both explicitly: RSN (WPA2) protocol, no PMF, single AKM.
        nmcli connection modify "$HOTSPOT_NAME" wifi-sec.proto rsn
        nmcli connection modify "$HOTSPOT_NAME" wifi-sec.pmf disable
        # Pin band + a channel inside the universally-unrestricted 1-11 range,
        # so no ESP32 regulatory-domain default excludes it from the scan.
        nmcli connection modify "$HOTSPOT_NAME" 802-11-wireless.band bg 802-11-wireless.channel 6

        echo "[+] Assigning baseline IP $CIDR with NetworkManager-managed DHCP & DNS..."
        nmcli connection modify "$HOTSPOT_NAME" ipv4.method shared ipv4.addresses "$CIDR"
        # No upstream router will ever answer a router-solicitation on this AP,
        # so ipv6.method=auto (the profile default) spends ~30s waiting for one,
        # then fails the whole connection with 'ip-config-unavailable' and tears
        # the AP down — which looked like it was "switching back" to Wi-Fi.
        nmcli connection modify "$HOTSPOT_NAME" ipv6.method disabled
        nmcli connection modify "$HOTSPOT_NAME" connection.autoconnect no

        echo "[+] Broadcasting '$HOTSPOT_NAME' hardware profile..."
        sleep 1
        nmcli connection up "$HOTSPOT_NAME"

        echo "[SUCCESS] Hotspot Active. Pi IP: $HOTSPOT_IP. NetworkManager is serving DHCP & DNS."
        ;;

    --wifi)
        echo "[+] Tearing down '$HOTSPOT_NAME' broadcast framework..."
        nmcli connection down "$HOTSPOT_NAME" >/dev/null 2>&1
        nmcli connection delete "$HOTSPOT_NAME" >/dev/null 2>&1

        echo "[+] Restoring standard hardware power management..."
        iwconfig $IFACE power on >/dev/null 2>&1
        busctl set-property org.freedesktop.NetworkManager /org/freedesktop/NetworkManager org.freedesktop.NetworkManager ConnectivityCheckEnabled b true

        echo "[+] Re-triggering default client parameters (DHCP)..."
        nmcli device set $IFACE autoconnect yes
        nmcli device connect $IFACE

        echo "[+] Restarting AdGuardHome..."
        systemctl start AdGuardHome >/dev/null 2>&1
        echo "[SUCCESS] Connected back to regular Wi-Fi with default DHCP and remote DNS routing."
        ;;

    *)
        show_help
        ;;
esac

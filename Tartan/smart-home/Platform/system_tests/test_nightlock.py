import time
import requests
from datetime import datetime
import pytest

BASE = "http://localhost:8080"
HOUSE = "mse"
AUTH = ("admin", "1234")

STATE_URL  = f"{BASE}/smarthome/state/{HOUSE}"
UPDATE_URL = f"{BASE}/smarthome/update/{HOUSE}"

#openai chatgpt 5.2-2026-02-14, "I think there is a bug in these tests, please check them, and fix any error you find."

def post_update(payload):
    """Send update and return response"""
    print(f"\n→ POST {UPDATE_URL}")
    print(f"  Payload: {payload}")

    r = requests.post(
        UPDATE_URL,
        json=payload,
        auth=AUTH,
        headers={"Accept": "application/json"},
        timeout=10
    )

    print(f"← Status: {r.status_code}")
    print(f"← Body: {r.text[:200]}")

    if r.status_code >= 400:
        raise AssertionError(
            f"POST failed with {r.status_code}: {r.text}"
        )

    return r.text

def get_state():
    """Fetch current state as JSON and unwrap tartanHome"""
    r = requests.get(
        STATE_URL,
        auth=AUTH,
        headers={"Accept": "application/json"},
        timeout=10
    )

    if r.status_code != 200:
        raise AssertionError(
            f"GET state failed with {r.status_code}: {r.text}"
        )

    try:
        full_response = r.json()
        # CRITICAL FIX: Unwrap the nested structure
        if "tartanHome" in full_response:
            return full_response["tartanHome"]
        else:
            return full_response
    except ValueError:
        raise AssertionError(
            f"Non-JSON response: {r.text[:200]}"
        )

def wait_until(pred, timeout_s=5, poll_s=0.2):
    """Poll until predicate is true or timeout"""
    deadline = time.time() + timeout_s
    last = None

    while time.time() < deadline:
        last = get_state()
        if pred(last):
            return last
        time.sleep(poll_s)

    raise AssertionError(
        f"Timeout waiting for condition.\n"
        f"Last state: {last}"
    )

def baseline_state():
    """Get baseline state as a dict"""
    return {
        "proximity": "occupied",
        "door": "closed",
        "light": "off",
        "humidifier": "off",
        "alarmArmed": "disarmed",
        "alarmActive": "inactive",
        "hvacMode": "heat",
        "hvacState": "off",
    }

@pytest.fixture(autouse=True)
def setup_baseline():
    """Reset to known state before each test"""
    post_update(baseline_state())
    time.sleep(0.3)  # Let system stabilize
    yield

def test_connection():
    """Test that we can reach the platform at all"""
    try:
        r = requests.get(
            f"{BASE}/smarthome/state/{HOUSE}",
            auth=AUTH,
            timeout=5
        )
        print(f"Status: {r.status_code}")
        print(f"Response: {r.text[:500]}")
        assert r.status_code in [200, 401], f"Got {r.status_code}"
    except requests.exceptions.ConnectionError as e:
        print(f"Cannot connect to {BASE}")
        print("Check: docker-compose ps")
        print("Check: ports exposed correctly")
        raise

def test_which_fields_work():
    """See what the API actually accepts"""
    minimal = {"door": "closed"}

    try:
        r = requests.post(
            UPDATE_URL,
            json=minimal,
            auth=AUTH,
            headers={"Accept": "application/json"},
            timeout=10
        )
        print(f"Minimal update status: {r.status_code}")
        print(f"Response: {r.text}")

        # Now check state
        state = get_state()
        print(f"State fields: {state.keys()}")

    except Exception as e:
        print(f"Error: {e}")
        raise





def test_nightlock_during_night_hours():
    """Night lock should re-lock door when nightActive is true"""

    print(f"\n=== Test: Night Lock Active ===")

    # Set nightLockStart/End to bracket the current hour so the server computes nightActive=true
    current_hour = datetime.utcnow().hour
    payload = baseline_state()
    payload.update({
        "nightLockEnabled": "true",
        "lockState": "unlocked",
        "nightLockStart": str((current_hour - 1) % 24),
        "nightLockEnd": str((current_hour + 1) % 24),
    })

    post_update(payload)

    # Verify the config was set
    time.sleep(0.5)
    state = get_state()
    print(f"\n=== Config Verification ===")
    print(f"nightLockEnabled: {state.get('nightLockEnabled')}")
    print(f"lockState: {state.get('lockState')}")

    assert state.get('nightLockEnabled') == 'true', \
        f"Night lock not enabled: got {state.get('nightLockEnabled')}"

    # Should relock within a couple of polling cycles (~10s)
    print("\n=== Waiting for auto-lock ===")
    final = wait_until(
        lambda s: s.get("lockState") == "locked",
        timeout_s=15
    )

    assert final["lockState"] == "locked", \
        "Door should be locked during night hours with night lock enabled"
    print("✓ Night lock successfully locked the door")

def test_nightlock_outside_night_hours():
    """Night lock should NOT lock door when nightActive is false"""

    print(f"\n=== Test: Night Lock Not Active ===")

    # Set nightLockStart/End to a window that does NOT include the current hour
    current_hour = datetime.utcnow().hour
    payload = baseline_state()
    payload.update({
        "nightLockEnabled": "true",
        "lockState": "unlocked",
        "nightLockStart": str((current_hour + 5) % 24),
        "nightLockEnd": str((current_hour + 7) % 24),
    })

    post_update(payload)

    time.sleep(1.0)  # Wait to ensure no auto-lock
    state = get_state()

    assert state["lockState"] == "unlocked", \
        "Door should remain unlocked outside night hours"
    print("✓ Night lock correctly did NOT lock door outside night hours")

def test_nightlock_disabled():
    """Disabled night lock should never auto-lock, even during night hours"""

    print(f"\n=== Test: Night Lock Disabled ===")

    # Set time window that includes current hour, but disable the feature
    current_hour = datetime.utcnow().hour
    payload = baseline_state()
    payload.update({
        "nightLockEnabled": "false",
        "lockState": "unlocked",
        "nightLockStart": str((current_hour - 1) % 24),
        "nightLockEnd": str((current_hour + 1) % 24),
    })

    post_update(payload)

    time.sleep(1.0)
    state = get_state()

    assert state["lockState"] == "unlocked", \
        "Door should remain unlocked when night lock is disabled"
    print("✓ Disabled night lock correctly did NOT lock door")


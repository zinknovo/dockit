import urllib.request
from urllib.error import HTTPError, URLError

def test_endpoint():
    url = "https://dockit-api.onrender.com/health"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=60) as response:
            assert response.status == 200, f"Expected 200, got {response.status}"
            data = response.read().decode('utf-8')
            assert 'ok' in data, f"Expected 'ok' in response, got {data}"
            print("Test Passed: Endpoint is reachable and returned 200.")
    except HTTPError as e:
        assert False, f"HTTPError: {e.code} {e.reason}"
    except URLError as e:
        assert False, f"URLError: {e.reason}"

if __name__ == "__main__":
    test_endpoint()

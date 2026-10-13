"""Validation reelle du TP. Python 3, Docker et le port 80 libre sont requis.

Ne supprime que les conteneurs crees par cette execution. Le volume est conserve.
Les sorties brutes et les mesures sont ecrites dans evidence/.
"""
from pathlib import Path
import concurrent.futures
import datetime
import json
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "evidence"
EVIDENCE.mkdir(exist_ok=True)
records = {}


def run(name, *args, check=True):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True,
                            encoding="utf-8", errors="replace")
    text = "$ " + " ".join(args) + "\n" + result.stdout + result.stderr
    (EVIDENCE / (name + ".txt")).write_text(text, encoding="utf-8")
    print(text, flush=True)
    if check and result.returncode:
        raise RuntimeError(f"{name}: exit {result.returncode}")
    return result


def request(path="/api/visits"):
    with urllib.request.urlopen("http://localhost" + path, timeout=12) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if path == "/api/visits" else body


def wait_ready():
    for _ in range(60):
        try:
            if request("/health") == "UP":
                return
        except (OSError, urllib.error.HTTPError):
            pass
        time.sleep(2)
    raise RuntimeError("Le service n'est pas pret apres 120 secondes")


def redis_value():
    result = subprocess.run(["docker", "exec", "db-service", "redis-cli", "GET", "hits"],
                            capture_output=True, text=True, check=True)
    return int(result.stdout.strip() or "0")


def main():
    # Refuser de remplacer des ressources existantes.
    for kind, name in [("container", "db-service"), ("container", "counter-web"),
                       ("network", "app-network"), ("volume", "redis-data")]:
        if subprocess.run(["docker", kind, "inspect", name], capture_output=True).returncode == 0:
            raise RuntimeError(f"Ressource deja presente : {name}; verifier son proprietaire.")
    run("01-version", "docker", "version")
    run("02-images", "docker", "images", "counter-app:1.0", "--format",
        "table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}")
    image = json.loads(run("03-image-details", "docker", "image", "inspect", "counter-app:1.0").stdout)[0]
    records["image"] = {k: image[k] for k in ["Id", "Size", "Created"]}
    run("04-network-create", "docker", "network", "create", "app-network")
    run("05-network-empty", "docker", "network", "inspect", "app-network")
    run("06-volume-create", "docker", "volume", "create", "--label",
        "com.docker.compose.project=tp1-visit-counter", "--label",
        "com.docker.compose.volume=redis-data", "redis-data")
    redis_args = ["docker", "run", "-d", "--name", "db-service", "--network", "app-network",
                  "-v", "redis-data:/data", "redis:7.4-alpine", "redis-server",
                  "--appendonly", "yes", "--appendfsync", "always"]
    run("07-redis-run", *redis_args)
    run("08-web-run", "docker", "run", "-d", "--name", "counter-web", "--network", "app-network",
        "-p", "80:8080", "-e", "REDIS_HOST=db-service", "counter-app:1.0")
    wait_ready()
    run("09-network-connected", "docker", "network", "inspect", "app-network")
    run("10-non-root", "docker", "exec", "counter-web", "id")
    run("11-mounts", "docker", "inspect", "db-service", "--format", "{{json .Mounts}}")
    records["manual_visits"] = [request() for _ in range(3)]
    before = redis_value()
    run("12-remove-redis", "docker", "rm", "-f", "db-service")
    try:
        request("/")
        raise AssertionError("Une panne Redis doit retourner HTTP 503")
    except urllib.error.HTTPError as error:
        assert error.code == 503, error.code
        records["outage"] = {"status": error.code, "body": error.read().decode("utf-8")}
    run("13-recreate-redis", *redis_args)
    wait_ready()
    restored = redis_value()
    after = request()["hits"]
    assert restored == before and after == before + 1
    records["persistence"] = {"before": before, "restored": restored, "next_visit": after}
    (EVIDENCE / "14-persistence.txt").write_text(
        "Test reel : suppression forcee puis recreation de db-service\n"
        f"GET hits avant suppression : {before}\nHTTP pendant la panne : 503\n"
        f"GET hits apres recreation : {restored}\nVisite suivante : {after}\n"
        "Resultat : PASS (valeur conservee dans redis-data)\n", encoding="utf-8")
    # Passer du deploiement manuel a Compose sans perdre redis-data.
    run("15-stop-manual", "docker", "rm", "-f", "counter-web", "db-service")
    run("16-replace-network", "docker", "network", "rm", "app-network")
    run("17-compose-config", "docker", "compose", "config")
    run("18-compose-scale", "docker", "compose", "up", "-d", "--scale", "web=3")
    wait_ready()
    run("19-compose-ps", "docker", "compose", "ps")
    run("20-network-compose", "docker", "network", "inspect", "app-network")
    before_parallel = redis_value()
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        replies = list(pool.map(lambda _: request(), range(30)))
    ids = sorted({reply["container"] for reply in replies})
    values = sorted(reply["hits"] for reply in replies)
    assert len(ids) == 3, ids
    assert values == list(range(before_parallel + 1, before_parallel + 31)), values
    assert redis_value() == before_parallel + 30
    records["scaling"] = {"requests": 30, "containers": ids,
                          "before": before_parallel, "after": redis_value(),
                          "unique_hits": len(set(values)), "replies": replies}
    (EVIDENCE / "21-scaling.txt").write_text(
        "Test reel : 30 requetes HTTP, 8 clients concurrents\n"
        + "Conteneurs observes :\n" + "\n".join(ids)
        + f"\nCompteur : {before_parallel} -> {redis_value()}\n"
        + f"Valeurs uniques : {len(set(values))}/30\nResultat : PASS\n", encoding="utf-8")
    run("22-compose-logs", "docker", "compose", "logs", "--no-color", "--tail", "15")
    records["tested_at"] = datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=1))).isoformat()
    (EVIDENCE / "results.json").write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding="utf-8")
    print("VALIDATION COMPLETE : persistance, isolation et scaling OK", flush=True)


if __name__ == "__main__":
    main()

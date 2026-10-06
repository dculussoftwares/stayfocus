#!/usr/bin/env python3
"""Sync the parts of the backlog that the Terraform GitHub provider can't manage.

Runs in GitHub Actions after `terraform apply` in infra/github (see
.github/workflows/backlog.yml). Every step is idempotent:

  1. Sub-issues: each story becomes a sub-issue of its epic.
  2. Dependencies: each story is marked "blocked by" the stories it depends on.
  3. Projects v2 board: uses the existing org project (#11), links the repo,
     ensures the custom fields, adds every issue, sets Type / Size / Story ID,
     and sets an initial Status. Status values already set by people are never
     overwritten, with one exception: Backlog -> Ready once all dependencies are closed.

Usage: sync_project.py <backlog.json|-> [relations|board|all]
  relations  sub-issues + "blocked by" links (works with the workflow's GITHUB_TOKEN, issues: write)
  board      Projects v2 board (needs a GitHub App / token with org Projects read+write)
  all        both (default)
Input: `terraform output -json backlog`.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys

OWNER = os.environ.get("BACKLOG_OWNER", "dculussoftwares")
REPO = os.environ.get("BACKLOG_REPO", "stayfocus")
# The board is an existing org project: https://github.com/orgs/dculussoftwares/projects/11
PROJECT_NUMBER = int(os.environ.get("BACKLOG_PROJECT_NUMBER") or 11)
DRY_RUN = os.environ.get("DRY_RUN") == "1"

STATUS_OPTIONS = [
    ("Backlog", "GRAY", "Waiting on dependencies"),
    ("Ready", "BLUE", "All dependencies done; pick it up"),
    ("In progress", "YELLOW", "Someone is working on it"),
    ("In review", "PURPLE", "PR open"),
    ("Done", "GREEN", "Merged"),
]
SIZE_OPTIONS = [("S", "GREEN", "Up to half a day"), ("M", "YELLOW", "1-2 days"), ("L", "ORANGE", "3-5 days")]
TYPE_OPTIONS = [("Epic", "PURPLE", "Milestone epic"), ("Story", "BLUE", "PR-sized story")]


def gh(*args: str, input_: str | None = None, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(["gh", *args], input=input_, text=True, capture_output=True, check=check)


def rest(method: str, path: str, fields: dict | None = None, check: bool = True):
    args = ["api", "-X", method, "-H", "Accept: application/vnd.github+json", path]
    if method == "GET":
        args[1:1] = ["--paginate", "--slurp"]  # one JSON array of pages
    for k, v in (fields or {}).items():
        args += ["-F", f"{k}={v}"]
    if DRY_RUN and method != "GET":
        print(f"[dry-run] {method} {path} {fields}")
        return None
    res = gh(*args, check=False)
    if res.returncode != 0:
        if check:
            raise RuntimeError(f"{method} {path} failed: {res.stderr.strip()}")
        return None
    out = res.stdout.strip()
    if not out:
        return None
    data = json.loads(out)
    if method == "GET":
        return [item for page in data for item in (page if isinstance(page, list) else [page])]
    return data


def graphql(query: str, **variables):
    payload = json.dumps({"query": query, "variables": variables})
    res = gh("api", "graphql", "--input", "-", input_=payload, check=False)
    if res.returncode != 0 and not res.stdout.strip().startswith("{"):
        raise RuntimeError(f"GraphQL request failed: {res.stderr.strip()}")
    data = json.loads(res.stdout)
    if data.get("errors"):
        raise RuntimeError(json.dumps(data["errors"], indent=2))
    return data["data"]


def mutate(query: str, **variables):
    if DRY_RUN:
        print(f"[dry-run] mutation {query.split('(')[0].split()[-1]} {variables}")
        return None
    return graphql(query, **variables)


# ---------------------------------------------------------------------------
# Issue metadata
# ---------------------------------------------------------------------------

def load_issues() -> dict[int, dict]:
    """number -> {id (node), databaseId, state}"""
    issues: dict[int, dict] = {}
    cursor = None
    while True:
        data = graphql(
            """query($owner:String!,$repo:String!,$after:String){
                 repository(owner:$owner,name:$repo){
                   issues(first:100,after:$after,states:[OPEN,CLOSED]){
                     pageInfo{hasNextPage endCursor}
                     nodes{number id databaseId state}}}}""",
            owner=OWNER, repo=REPO, after=cursor,
        )
        page = data["repository"]["issues"]
        for n in page["nodes"]:
            issues[n["number"]] = n
        if not page["pageInfo"]["hasNextPage"]:
            return issues
        cursor = page["pageInfo"]["endCursor"]


# ---------------------------------------------------------------------------
# 1 + 2: sub-issues and dependencies (REST)
# ---------------------------------------------------------------------------

def sync_sub_issues(backlog: dict, issues: dict[int, dict]) -> None:
    for epic_id, epic in backlog["epics"].items():
        existing = {i["number"] for i in (rest("GET", f"repos/{OWNER}/{REPO}/issues/{epic['number']}/sub_issues") or [])}
        for story_id, story in sorted(backlog["stories"].items()):
            if story["epic"] != epic_id or story["number"] in existing:
                continue
            rest("POST", f"repos/{OWNER}/{REPO}/issues/{epic['number']}/sub_issues",
                 {"sub_issue_id": issues[story["number"]]["databaseId"]})
            print(f"sub-issue: {story_id} -> {epic_id}")


def sync_dependencies(backlog: dict, issues: dict[int, dict]) -> None:
    stories = backlog["stories"]
    for story_id, story in sorted(stories.items()):
        if not story["depends"]:
            continue
        path = f"repos/{OWNER}/{REPO}/issues/{story['number']}/dependencies/blocked_by"
        current = rest("GET", path, check=False)
        if current is None and not DRY_RUN:
            print("warning: issue dependencies API not available; skipping 'blocked by' links")
            return
        have = {i["number"] for i in (current or [])}
        for dep in story["depends"]:
            dep_number = stories[dep]["number"]
            if dep_number in have:
                continue
            rest("POST", path, {"issue_id": issues[dep_number]["databaseId"]})
            print(f"blocked-by: {story_id} <- {dep}")


# ---------------------------------------------------------------------------
# 3: Projects v2 board (GraphQL)
# ---------------------------------------------------------------------------

FIELDS_QUERY = """query($id:ID!){node(id:$id){... on ProjectV2{
  fields(first:50){nodes{
    ... on ProjectV2FieldCommon{id name dataType}
    ... on ProjectV2SingleSelectField{id name options{id name}}}}}}}"""


def ensure_project() -> str:
    """Use the existing board (PROJECT_NUMBER) and make sure the repo is linked to it. Never creates a project."""
    data = graphql(
        """query($org:String!,$repo:String!,$n:Int!){
             organization(login:$org){projectV2(number:$n){id title number repositories(first:50){nodes{name}}}}
             repository(owner:$org,name:$repo){id}}""",
        org=OWNER, repo=REPO, n=PROJECT_NUMBER,
    )
    proj = data["organization"]["projectV2"]
    if proj is None:
        raise SystemExit(f"org project #{PROJECT_NUMBER} not found or not accessible to this token")
    print(f"project: #{proj['number']} {proj['title']}")
    if REPO not in {r["name"] for r in proj["repositories"]["nodes"]}:
        try:
            mutate("""mutation($p:ID!,$r:ID!){linkProjectV2ToRepository(input:{projectId:$p,repositoryId:$r}){clientMutationId}}""",
                   p=proj["id"], r=data["repository"]["id"])
            print(f"project: linked {OWNER}/{REPO}")
        except RuntimeError as e:
            # Linking only adds the project to the repo's Projects tab; it needs repo admin rights the
            # project-only token doesn't have. Items can be added without it.
            print(f"warning: could not link the repo to the project ({e}); link it once in the project settings")
    return proj["id"]


def opts(options):
    return [{"name": n, "color": c, "description": d} for n, c, d in options]


def ensure_fields(project_id: str) -> dict[str, dict]:
    fields = {f["name"]: f for f in graphql(FIELDS_QUERY, id=project_id)["node"]["fields"]["nodes"] if f}

    status = fields.get("Status")
    wanted = [n for n, _, _ in STATUS_OPTIONS]
    if status and [o["name"] for o in status.get("options", [])] != wanted:
        try:
            mutate("""mutation($id:ID!,$o:[ProjectV2SingleSelectFieldOptionInput!]){
                        updateProjectV2Field(input:{fieldId:$id,singleSelectOptions:$o}){projectV2Field{... on ProjectV2SingleSelectField{id}}}}""",
                   id=status["id"], o=opts(STATUS_OPTIONS))
            print("field: Status options updated")
        except RuntimeError as e:
            print(f"warning: could not update Status options ({e}); set them in the project settings")

    for name, options in (("Type", TYPE_OPTIONS), ("Size", SIZE_OPTIONS)):
        if name not in fields:
            mutate("""mutation($p:ID!,$n:String!,$o:[ProjectV2SingleSelectFieldOptionInput!]){
                        createProjectV2Field(input:{projectId:$p,dataType:SINGLE_SELECT,name:$n,singleSelectOptions:$o}){clientMutationId}}""",
                   p=project_id, n=name, o=opts(options))
            print(f"field: {name} created")
    if "Story ID" not in fields:
        mutate("""mutation($p:ID!){createProjectV2Field(input:{projectId:$p,dataType:TEXT,name:"Story ID"}){clientMutationId}}""",
               p=project_id)
        print("field: Story ID created")

    return {f["name"]: f for f in graphql(FIELDS_QUERY, id=project_id)["node"]["fields"]["nodes"] if f}


def load_items(project_id: str) -> dict[str, dict]:
    """issue node id -> {item id, status, size, type, storyId}"""
    items, cursor = {}, None
    while True:
        data = graphql(
            """query($id:ID!,$after:String){node(id:$id){... on ProjectV2{
                 items(first:100,after:$after){pageInfo{hasNextPage endCursor} nodes{
                   id content{... on Issue{id}}
                   status:fieldValueByName(name:"Status"){... on ProjectV2ItemFieldSingleSelectValue{name}}
                   size:fieldValueByName(name:"Size"){... on ProjectV2ItemFieldSingleSelectValue{name}}
                   type:fieldValueByName(name:"Type"){... on ProjectV2ItemFieldSingleSelectValue{name}}
                   sid:fieldValueByName(name:"Story ID"){... on ProjectV2ItemFieldTextValue{text}}}}}}}""",
            id=project_id, after=cursor,
        )
        page = data["node"]["items"]
        for n in page["nodes"]:
            if n["content"]:
                items[n["content"]["id"]] = {
                    "id": n["id"],
                    "status": (n["status"] or {}).get("name"),
                    "size": (n["size"] or {}).get("name"),
                    "type": (n["type"] or {}).get("name"),
                    "sid": (n["sid"] or {}).get("text"),
                }
        if not page["pageInfo"]["hasNextPage"]:
            return items
        cursor = page["pageInfo"]["endCursor"]


def set_select(project_id, item_id, field, option_name):
    option = next((o for o in field.get("options", []) if o["name"] == option_name), None)
    if option is None:
        print(f"warning: option {option_name!r} missing in field {field['name']}")
        return
    mutate("""mutation($p:ID!,$i:ID!,$f:ID!,$o:String!){updateProjectV2ItemFieldValue(input:{
                projectId:$p,itemId:$i,fieldId:$f,value:{singleSelectOptionId:$o}}){clientMutationId}}""",
           p=project_id, i=item_id, f=field["id"], o=option["id"])


def set_text(project_id, item_id, field, text):
    mutate("""mutation($p:ID!,$i:ID!,$f:ID!,$t:String!){updateProjectV2ItemFieldValue(input:{
                projectId:$p,itemId:$i,fieldId:$f,value:{text:$t}}){clientMutationId}}""",
           p=project_id, i=item_id, f=field["id"], t=text)


def sync_board(backlog: dict, issues: dict[int, dict]) -> None:
    project_id = ensure_project()
    fields = ensure_fields(project_id)
    items = load_items(project_id)
    stories = backlog["stories"]

    entries = [(eid, e["number"], "Epic", None, []) for eid, e in backlog["epics"].items()]
    entries += [(sid, s["number"], "Story", s["size"], s["depends"]) for sid, s in stories.items()]

    for key, number, kind, size, depends in sorted(entries):
        issue = issues[number]
        item = items.get(issue["id"])
        if item is None:
            res = mutate("""mutation($p:ID!,$c:ID!){addProjectV2ItemById(input:{projectId:$p,contentId:$c}){item{id}}}""",
                         p=project_id, c=issue["id"])
            if res is None:
                continue
            item = {"id": res["addProjectV2ItemById"]["item"]["id"], "status": None, "size": None, "type": None, "sid": None}
            print(f"board: added {key}")

        if item["type"] != kind:
            set_select(project_id, item["id"], fields["Type"], kind)
        if size and item["size"] != size:
            set_select(project_id, item["id"], fields["Size"], size)
        if item["sid"] != key:
            set_text(project_id, item["id"], fields["Story ID"], key)

        deps_done = all(issues[stories[d]["number"]]["state"] == "CLOSED" for d in depends)
        if issue["state"] == "CLOSED":
            target = "Done"
        elif kind == "Epic":
            target = "Backlog"
        else:
            target = "Ready" if deps_done else "Backlog"
        # Only set a status that is empty, or promote Backlog -> Ready / anything -> Done.
        if item["status"] is None or (item["status"] == "Backlog" and target == "Ready") or (
            target == "Done" and item["status"] != "Done"
        ):
            if item["status"] != target:
                set_select(project_id, item["id"], fields["Status"], target)
                print(f"board: {key} status {item['status']} -> {target}")


def main() -> None:
    src = sys.stdin if len(sys.argv) < 2 or sys.argv[1] == "-" else open(sys.argv[1])
    raw = json.load(src)
    backlog = raw.get("value", raw)  # accept either `terraform output -json backlog` or the full output object
    mode = sys.argv[2] if len(sys.argv) > 2 else "all"
    if mode not in ("relations", "board", "all"):
        raise SystemExit(f"unknown mode {mode!r}")
    issues = load_issues()
    if mode in ("relations", "all"):
        sync_sub_issues(backlog, issues)
        sync_dependencies(backlog, issues)
    if mode in ("board", "all"):
        sync_board(backlog, issues)


if __name__ == "__main__":
    main()

# WayPoint — Campus Guide

## 1. Project Overview
WayPoint is a campus guide web application.
Its purpose is to help users navigate campus information
and interact with campus-related content.

## 2. Technology Stack
- Backend: Java
- Web framework/libraries: Check pom.xml
- Database: MySQL
- Frontend: HTML, CSS, JavaScript
- Build tool: Maven

## 3. Project Structure
- src/main/java/com/waypoint/
  - Main.java: Application entry point
  - Database.java: Database connection
  - AuthController.java: Authentication
  - UserDao.java: User data operations
  - PostController.java: Post-related operations
  - PostDao.java: Post data operations
  - CommentController.java: Comment operations
  - CommentDao.java: Comment data operations
  - IssueController.java: Issue-related operations
  - IssueDao.java: Issue data operations
  - LocationDao.java: Location data operations
  - SearchController.java: Search functionality
- src/main/resources/public/
  - HTML pages
  - CSS styles
  - JavaScript files
- pom.xml: Maven dependencies and build configuration

## 4. Database
The application uses MySQL.
Inspect Database.java to understand the connection setup.
Never commit database passwords, API keys, or other secrets.

## 5. Instructions for AI Coding Assistants
Before modifying anything:
1. Inspect the entire project structure.
2. Read pom.xml and README.md.
3. Read all Java source files.
4. Inspect the HTML, CSS, and JavaScript files.
5. Understand the existing database schema and queries.
6. Identify how the frontend communicates with the backend.
7. Preserve existing functionality when making changes.
8. Explain significant changes and list modified files.
9. Do not invent database tables or columns; verify them first.
10. Never expose secrets or overwrite data without confirmation.

## 6. Development Rules
- Keep changes compatible with the existing Java version.
- Follow the project's existing coding style.
- Avoid unnecessary dependencies.
- Test changes before declaring a task complete.
- Do not modify generated files inside target/.
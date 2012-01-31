/**
 * Marmoset: a student project snapshot, submission, testing and code review
 * system developed by the Univ. of Maryland, College Park
 * 
 * Developed as part of Jaime Spacco's Ph.D. thesis work, continuing effort led
 * by William Pugh. See http://marmoset.cs.umd.edu/
 * 
 * Copyright 2005 - 2011, Univ. of Maryland
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 */

package edu.umd.cs.submitServer.servlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.umd.cs.marmoset.modelClasses.Course;
import edu.umd.cs.marmoset.modelClasses.Student;
import edu.umd.cs.marmoset.modelClasses.StudentPicture;

public class SyncStudents extends GradeServerInterfaceServlet {

	/**
	 * The doPost method of the servlet. <br>
	 *
	 * This method is called when a form has its tag value method equals to
	 * post.
	 *
	 * @param request
	 *            the request send by the client to the server
	 * @param response
	 *            the response send by the server to the client
	 * @throws ServletException
	 *             if an error occurred
	 * @throws IOException
	 *             if an error occurred
	 */
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		Connection gradesConn = null;
		Connection conn = null;
		response.setContentType("text/plain");
		try {
			conn = getConnection();
			gradesConn = getGradesConnection();
			PrintWriter writer = response.getWriter();
			if (true) {
				Map<Integer, Student> allStudents = Student.lookupAll(conn);
				for (Student student : allStudents.values()) {
					syncStudent(writer, student, gradesConn, conn);
				}
			}
			writer.flush();
			if (true) {
				Collection<Course> courses = Course.lookupAll(conn);
				for (Course c : courses) {
					String courseIds = c.getCourseIDs();
					if (courseIds == null || courseIds.length() == 0)
						continue;
					for (String courseId : courseIds.split(",")) {
						ImportCourse.importStudents(writer, c.getSemester(),
								c, Integer.parseInt(courseId), false, gradesConn, conn);
						writer.flush();
					}
					
				}
			}

		} catch (SQLException e) {
			throw new ServletException(e);
		} finally {
			releaseConnection(conn);
			releaseGradesConnection(gradesConn);
		}

	}

	private void syncStudent(PrintWriter writer, Student student, Connection gradesConn,
			Connection conn) throws SQLException {
		{
			String query = "SELECT lastName, firstName, nickname, directoryID, email"

				+ " FROM submitexport " + " WHERE uid = ? LIMIT 1";
		PreparedStatement stmt = gradesConn.prepareStatement(query);
		stmt.setString(1, student.getCampusUID());

		ResultSet rs = stmt.executeQuery();

		if (rs.next()) {
			int col = 1;
			String lastname = rs.getString(col++);
			String firstname = rs.getString(col++);
			String nickname = rs.getString(col++);

			if (nickname != null)
			    firstname = nickname;
			
			String loginName = rs.getString(col++);
			String email = rs.getString(col++);


				boolean same = lastname.equals(student.getLastname())
						&& firstname.equals(student.getFirstname())
						&& (student.hasLoginSuffix() || loginName
								.equals(student.getLoginName()))
						&& email.equals(student.getEmail());
				if (!same) {
					student.setLastname(lastname);
					student.setFirstname(firstname);
					if (!student.hasLoginSuffix())
						student.setLoginName(loginName);
					student.setEmail(email);
					writer.printf("Updated %s %s%n", firstname, lastname);
				}
				loadStudentPicture(student, gradesConn, conn);

				student.update(conn);
		}
		rs.close();

		stmt.close();
		}


	}

	public static  boolean loadStudentPicture(Student student, Connection gradesConn,
			Connection conn) throws SQLException {
		String query = "SELECT type, image" + " FROM photos "
				+ " WHERE uid = ? LIMIT 1";

		PreparedStatement stmt = gradesConn.prepareStatement(query);
		stmt.setString(1, student.getCampusUID());
		ResultSet rs = stmt.executeQuery();
		if (rs.next()) {
			String type = rs.getString(1);
			Blob blob = rs.getBlob(2);
			StudentPicture.insertOrUpdate(conn, student, type, blob);
			student.setHasPicture(true);

		}
		rs.close();
		stmt.close();
		return student.getHasPicture();
	}

}

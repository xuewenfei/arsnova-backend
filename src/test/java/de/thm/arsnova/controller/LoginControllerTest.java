/*
 * Copyright (C) 2012 THM webMedia
 * 
 * This file is part of ARSnova.
 *
 * ARSnova is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ARSnova is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.thm.arsnova.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.servlet.ModelAndView;

import de.thm.arsnova.AbstractSpringContextTestBase;

@ContextConfiguration(locations = "file:src/main/webapp/WEB-INF/arsnova-servlet.xml")
@RunWith(SpringJUnit4ClassRunner.class)
public class LoginControllerTest extends AbstractSpringContextTestBase {

	
	@Before
	public void setUp() throws Exception {
		this.request = new MockHttpServletRequest();
		this.response = new MockHttpServletResponse();
	}
	
	@Test
	public void testCasLogin() throws Exception {
		request.setMethod("GET");
        request.setRequestURI("/doCasLogin");

        final ModelAndView mav = handle(request, response);
        assertEquals(null, mav.getViewName());
	}
	
	@Test
	public void testGuestLogin() throws Exception {
		request.setMethod("GET");
        request.setRequestURI("/doGuestLogin");

        final ModelAndView mav = handle(request, response);
        
        assertTrue(mav.getViewName().startsWith("redirect:/"));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(auth.getClass(), UsernamePasswordAuthenticationToken.class);
	}
	
	
	
}

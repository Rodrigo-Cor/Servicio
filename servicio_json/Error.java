/*
  Error.java
  Permite regresar al cliente REST un mensaje de error
  Carlos Pineda Guerrero, abril 2023
*/

package servicio_json;

public class Error
{
	String message;

	Error(String message)
	{
		this.message = message;
	}
}
